package com.platon.mtool.client.execute.observe;

import com.alibaba.fastjson.JSON;
import com.beust.jcommander.JCommander;
import com.platon.contracts.ppos.StakingContract;
import com.platon.mtool.client.ClientConsts;
import com.platon.mtool.client.execute.MtoolExecutor;
import com.platon.mtool.client.options.IncreaseStakingOption;
import com.platon.mtool.client.service.BlockChainService;
import com.platon.mtool.client.tools.PrintUtils;
import com.platon.mtool.client.tools.ProgressBar;
import com.platon.mtool.client.tools.ResourceUtils;
import com.platon.mtool.common.entity.AdditionalInfo;
import com.platon.mtool.common.entity.ValidatorConfig;
import com.platon.mtool.common.enums.FuncTypeEnum;
import com.platon.mtool.common.logger.Log;
import com.platon.mtool.common.utils.HashUtil;
import com.platon.mtool.common.utils.LogUtils;
import com.platon.mtool.common.utils.MtoolCsvFileUtil;
import com.platon.mtool.common.web3j.MtoolTransactionManager;
import com.platon.mtool.common.web3j.TransactionEntity;
import com.platon.protocol.Web3j;
import com.platon.protocol.core.methods.response.PlatonSendTransaction;
import com.platon.tx.TransactionManager;
import com.platon.tx.gas.GasProvider;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * 观察钱包增持质押
 *
 * <p>Created by liyf.
 */
public class ObserveIncreaseStakingExecutor extends MtoolExecutor<IncreaseStakingOption> {

  private static final Logger logger =
      LoggerFactory.getLogger(ObserveIncreaseStakingExecutor.class);
  private BlockChainService blockChainService = BlockChainService.singleton();

  public ObserveIncreaseStakingExecutor(JCommander commander, IncreaseStakingOption commonOption) {
    super(commander, commonOption);
  }

  @Override
  public void execute(IncreaseStakingOption option) throws Exception {
    LogUtils.info(logger, () -> Log.newBuilder().msg("Increasestaking").kv("option", option));
    ProgressBar.start();
    ValidatorConfig validatorConfig = option.getConfig();
    Web3j web3j = com.platon.mtool.common.web3j.Web3jUtil.getFromConfig(validatorConfig);

    blockChainService.validSelfStakingAddress(
        web3j, validatorConfig.getNodePublicKey(), option.getKeystore().getAddress());

    // 检查当前金额是否满足链上的最小增持质押金额
    blockChainService.validAmount(web3j,"staking","operatingThreshold",option.getAmount().getAmount());

    String targetChainAddress = option.getKeystore().getAddress();
    TransactionManager transactionManager =
        new MtoolTransactionManager(web3j, targetChainAddress);
    StakingContract stakingContract = StakingContract.load(web3j, transactionManager);
    GasProvider gasProvider =
        checkGasPrice(stakingContract.getAddStakingGasProvider(
                validatorConfig.getNodePublicKey(),
                option.getAmount().getAmountType(),
                option.getAmount().getAmount()));
    blockChainService.validBalanceEnough(
        option.getKeystore().getAddress(), option.getAmount().getAmount(), gasProvider, web3j,option.getAmount().getAmountType());
    PlatonSendTransaction transaction =
        stakingContract
            .addStakingReturnTransaction(
                validatorConfig.getNodePublicKey(),
                option.getAmount().getAmountType(),
                option.getAmount().getAmount(),
                gasProvider)
            .send();

    TransactionEntity entity =
        JSON.parseObject(transaction.getTransactionHash(), TransactionEntity.class);
    entity.setType(FuncTypeEnum.INCREASESTAKING);
    entity.setAccountType(option.getAmount().getAmountType().name());
    entity.setAmount(option.getAmount().getAmount());
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
