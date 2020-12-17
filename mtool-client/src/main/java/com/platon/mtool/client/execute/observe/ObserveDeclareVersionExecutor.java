package com.platon.mtool.client.execute.observe;

import com.alaya.contracts.ppos.dto.enums.StakingAmountType;
import com.alibaba.fastjson.JSON;
import com.beust.jcommander.JCommander;
import com.platon.mtool.client.ClientConsts;
import com.platon.mtool.client.execute.MtoolExecutor;
import com.platon.mtool.client.options.DeclareVersionOption;
import com.platon.mtool.client.service.BlockChainService;
import com.platon.mtool.client.tools.PrintUtils;
import com.platon.mtool.client.tools.ProgressBar;
import com.platon.mtool.client.tools.ResourceUtils;
import com.platon.mtool.common.entity.AdditionalInfo;
import com.platon.mtool.common.entity.ValidatorConfig;
import com.platon.mtool.common.enums.FuncTypeEnum;
import com.platon.mtool.common.logger.Log;
import com.platon.mtool.common.utils.AddressUtil;
import com.platon.mtool.common.utils.HashUtil;
import com.platon.mtool.common.utils.LogUtils;
import com.platon.mtool.common.utils.MtoolCsvFileUtil;
import com.platon.mtool.common.web3j.MtoolTransactionManager;
import com.platon.mtool.common.web3j.TransactionEntity;
import com.alaya.contracts.ppos.ProposalContract;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.alaya.protocol.core.methods.response.bean.ProgramVersion;
import com.alaya.protocol.Web3j;
import com.alaya.protocol.core.methods.response.PlatonSendTransaction;
import com.alaya.tx.TransactionManager;
import com.alaya.tx.gas.GasProvider;

import java.io.File;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static com.platon.mtool.client.tools.CliConfigUtils.CLIENT_CONFIG;

/**
 * 观察钱包版本声明
 *
 * <p>Created by liyf.
 */
public class ObserveDeclareVersionExecutor extends MtoolExecutor<DeclareVersionOption> {

  private static final Logger logger = LoggerFactory.getLogger(ObserveDeclareVersionExecutor.class);
  private BlockChainService blockChainService = BlockChainService.singleton();

  public ObserveDeclareVersionExecutor(JCommander commander, DeclareVersionOption commonOption) {
    super(commander, commonOption);
  }

  @Override
  public void execute(DeclareVersionOption option) throws Exception {
    LogUtils.info(logger, () -> Log.newBuilder().msg("DeclareVersion").kv("option", option));
    ProgressBar.start();
    ValidatorConfig validatorConfig = option.getConfig();

    Web3j web3j = com.platon.mtool.common.web3j.Web3jUtil.getFromConfig(validatorConfig);

    blockChainService.validSelfStakingAddress(
        web3j, validatorConfig.getNodePublicKey(), option.getKeystore().getAddress());

    String targetChainAddress = AddressUtil.getTargetChainAccountAddress(CLIENT_CONFIG.getTargetChainId(),option.getKeystore().getAddress().getMainnet());
    TransactionManager transactionManager =
        new MtoolTransactionManager(
            web3j, targetChainAddress, CLIENT_CONFIG.getTargetChainId());
    ProposalContract proposalContract = ProposalContract.load(web3j, transactionManager,CLIENT_CONFIG.getTargetChainId());
    ProgramVersion programVersion = web3j.getProgramVersion().send().getAdminProgramVersion();
    GasProvider gasProvider =
        proposalContract.getDeclareVersionGasProvider(
            programVersion, validatorConfig.getNodePublicKey());
    blockChainService.validBalanceEnough(
        option.getKeystore().getAddress(), BigInteger.ZERO, gasProvider, web3j, StakingAmountType.FREE_AMOUNT_TYPE);
    PlatonSendTransaction transaction =
        proposalContract
            .declareVersionReturnTransaction(
                programVersion, validatorConfig.getNodePublicKey(), gasProvider)
            .send();

    TransactionEntity entity =
        JSON.parseObject(transaction.getTransactionHash(), TransactionEntity.class);
    entity.setType(FuncTypeEnum.DECLARE_VERSION);
    entity.setAccountType("");
    entity.setAmount(BigInteger.ZERO);
    AdditionalInfo additionalInfo = new AdditionalInfo();
    BeanUtils.copyProperties(additionalInfo, validatorConfig);
    entity.setAdditionalInfo(JSON.toJSONString(additionalInfo));

    entity.setHash(HashUtil.hashTransaction(entity));
    byte[] bytes = MtoolCsvFileUtil.toTransactionDetailBytes(Collections.singletonList(entity));
    String filename =
        String.format(
            "transaction_detail_%s.csv",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
    String filepath =
        ResourceUtils.getTransactionDetailsPath().resolve(filename).toAbsolutePath().toString();
    FileUtils.writeByteArrayToFile(new File(filepath), bytes);

    ProgressBar.stop();
    PrintUtils.echo(ClientConsts.SUCCESS);
    PrintUtils.echo("File generated on %s", filepath);
  }
}
