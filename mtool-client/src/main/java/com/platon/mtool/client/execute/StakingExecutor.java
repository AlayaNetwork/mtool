package com.platon.mtool.client.execute;

import com.beust.jcommander.JCommander;
import com.platon.contracts.ppos.StakingContract;
import com.platon.contracts.ppos.dto.TransactionResponse;
import com.platon.contracts.ppos.dto.req.StakingParam;
import com.platon.crypto.Credentials;
import com.platon.mtool.client.options.StakingOption;
import com.platon.mtool.client.service.BlockChainService;
import com.platon.mtool.client.tools.ProgressBar;
import com.platon.mtool.common.AllCommands;
import com.platon.mtool.common.entity.ValidatorConfig;
import com.platon.mtool.common.logger.Log;
import com.platon.mtool.common.utils.LogUtils;
import com.platon.protocol.Web3j;
import com.platon.protocol.core.RemoteCall;
import com.platon.protocol.core.methods.response.PlatonSendTransaction;
import com.platon.tx.gas.GasProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/**
 * 质押
 *
 * <p>Created by liyf.
 */
public class StakingExecutor extends MtoolExecutor<StakingOption> {

  private static final Logger logger = LoggerFactory.getLogger(StakingExecutor.class);

  private BlockChainService blockChainService = BlockChainService.singleton();

  public StakingExecutor(JCommander commander, StakingOption commonOption) {
    super(commander, commonOption);
  }

  protected Web3j getWeb3j(ValidatorConfig validatorConfig) {
    return com.platon.mtool.common.web3j.Web3jUtil.getFromConfig(validatorConfig);
  }

  protected StakingContract getStakingContract(Web3j web3j, Credentials credentials) {
    return StakingContract.load(web3j, credentials);
  }

  @Override
  public void execute(StakingOption option) throws Exception {
    LogUtils.info(logger, () -> Log.newBuilder().msg(AllCommands.STAKING).kv("option", option));
    ProgressBar.start();

    ValidatorConfig validatorConfig = option.getConfig();
    Web3j web3j = getWeb3j(validatorConfig);

    // 检查当前金额是否满足链上的最小质押金额
    blockChainService.validAmount(web3j,"staking","stakeThreshold",option.getAmount().getAmount());

    Credentials credentials = option.getKeystore().getCredentials();
    StakingContract stakingContract =
        getStakingContract(web3j, credentials);


    StakingParam stakingParam =
        new StakingParam.Builder()
            .setNodeId(validatorConfig.getNodePublicKey())
            .setAmount(option.getAmount().getAmount())
            .setStakingAmountType(option.getAmount().getAmountType())
            .setBenifitAddress(option.getBenefitAddress())
            .setExternalId(option.getExternalId())
            .setNodeName(option.getNodeName())
            .setWebSite(option.getWebsite())
            .setDetails(option.getDetails())
            .setBlsPubKey(validatorConfig.getBlsPubKey())
            // 奖励分配新增字段
            .setRewardPer(BigInteger.valueOf(option.getDelegateRewardPercent()))
            // 手续费新增字段
            .setProcessVersion(web3j.getProgramVersion().send().getAdminProgramVersion())
            .setBlsProof(web3j.getSchnorrNIZKProve().send().getAdminSchnorrNIZKProve())
            .build();
    GasProvider gasProvider = checkGasPrice(stakingContract.getStakingGasProvider(stakingParam));
    blockChainService.validBalanceEnough(
        option.getKeystore().getAddress(), option.getAmount().getAmount(), gasProvider, web3j,option.getAmount().getAmountType());

    RemoteCall<PlatonSendTransaction> remoteCall = stakingContract.stakingReturnTransaction(stakingParam, gasProvider);

    PlatonSendTransaction transaction = remoteCall.send();

    TransactionResponse response = stakingContract.getTransactionResponse(transaction).send();

    LogUtils.info(logger, () -> Log.newBuilder().msg(AllCommands.STAKING).kv("transaction", transaction));
    LogUtils.info(logger, () -> Log.newBuilder().msg(AllCommands.STAKING).kv("response", response));

    ProgressBar.stop();

    echoResult(
        transaction,
        response,
        validatorConfig.getNodePublicKey(),
        blockChainService.getCostAmount(option.getAmount().getAmount(), gasProvider));
  }
}
