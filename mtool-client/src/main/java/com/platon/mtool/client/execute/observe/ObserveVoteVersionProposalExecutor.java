package com.platon.mtool.client.execute.observe;

import com.alibaba.fastjson.JSON;
import com.beust.jcommander.JCommander;
import com.platon.contracts.ppos.ProposalContract;
import com.platon.contracts.ppos.dto.common.ProposalType;
import com.platon.contracts.ppos.dto.enums.StakingAmountType;
import com.platon.contracts.ppos.dto.enums.VoteOption;
import com.platon.mtool.client.ClientConsts;
import com.platon.mtool.client.execute.MtoolExecutor;
import com.platon.mtool.client.options.VoteVersionProposalOption;
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
import com.platon.protocol.core.methods.response.bean.ProgramVersion;
import com.platon.tx.TransactionManager;
import com.platon.tx.gas.GasProvider;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * 观察钱包版本提案投票
 *
 * <p>Created by liyf.
 */
public class ObserveVoteVersionProposalExecutor extends MtoolExecutor<VoteVersionProposalOption> {

  private static final Logger logger =
      LoggerFactory.getLogger(ObserveVoteVersionProposalExecutor.class);
  private BlockChainService blockChainService = BlockChainService.singleton();

  public ObserveVoteVersionProposalExecutor(
      JCommander commander, VoteVersionProposalOption commonOption) {
    super(commander, commonOption);
  }

  @Override
  public void execute(VoteVersionProposalOption option) throws Exception {
    LogUtils.info(logger, () -> Log.newBuilder().msg("VoteVersion").kv("option", option));
    ProgressBar.start();
    ValidatorConfig validatorConfig = option.getConfig();
    Web3j web3j = com.platon.mtool.common.web3j.Web3jUtil.getFromConfig(validatorConfig);

    blockChainService.validSelfStakingAddress(
        web3j, validatorConfig.getNodePublicKey(), option.getKeystore().getAddress());
    blockChainService.validVoteProposal(
        web3j, ProposalType.VERSION_PROPOSAL, option.getProposalid());
    String targetChainAddress = option.getKeystore().getAddress();

    TransactionManager transactionManager =
        new MtoolTransactionManager(
            web3j, targetChainAddress);
    ProposalContract proposalContract = ProposalContract.load(web3j, transactionManager);
    ProgramVersion programVersion = web3j.getProgramVersion().send().getAdminProgramVersion();
    GasProvider gasProvider =
        checkGasPrice(
                proposalContract.getVoteProposalGasProvider(
                        programVersion,
                        VoteOption.YEAS,
                        option.getProposalid(),
                        validatorConfig.getNodePublicKey())
        );
    blockChainService.validBalanceEnough(
        option.getKeystore().getAddress(), BigInteger.ZERO, gasProvider, web3j, StakingAmountType.FREE_AMOUNT_TYPE);
    PlatonSendTransaction transaction =
        proposalContract
            .voteReturnTransaction(
                programVersion,
                option.getProposalid(),
                validatorConfig.getNodePublicKey(),
                VoteOption.YEAS,
                gasProvider)
            .send();

    TransactionEntity entity =
        JSON.parseObject(transaction.getTransactionHash(), TransactionEntity.class);
    entity.setType(FuncTypeEnum.VOTE_VERSION_PROPOSAL);
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
