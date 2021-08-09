package com.platon.mtool.client.execute;

import com.beust.jcommander.JCommander;
import com.platon.contracts.ppos.StakingContract;
import com.platon.contracts.ppos.dto.CallResponse;
import com.platon.contracts.ppos.dto.TransactionResponse;
import com.platon.contracts.ppos.dto.enums.StakingAmountType;
import com.platon.contracts.ppos.dto.req.UpdateStakingParam;
import com.platon.contracts.ppos.dto.resp.Node;
import com.platon.crypto.Credentials;
import com.platon.mtool.client.options.UpdateValidatorOption;
import com.platon.mtool.client.service.BlockChainService;
import com.platon.mtool.client.tools.ProgressBar;
import com.platon.mtool.common.AllCommands;
import com.platon.mtool.common.entity.ValidatorConfig;
import com.platon.mtool.common.exception.MtoolClientException;
import com.platon.mtool.common.logger.Log;
import com.platon.mtool.common.utils.LogUtils;
import com.platon.protocol.Web3j;
import com.platon.protocol.core.methods.response.PlatonSendTransaction;
import com.platon.tx.gas.GasProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/**
 * 更新验证人
 *
 * <p>Created by liyf.
 */
public class UpdateValidatorExecutor extends MtoolExecutor<UpdateValidatorOption> {

  private static final Logger logger = LoggerFactory.getLogger(UpdateValidatorExecutor.class);
  private BlockChainService blockChainService = BlockChainService.singleton();

  public UpdateValidatorExecutor(JCommander commander, UpdateValidatorOption commonOption) {
    super(commander, commonOption);
  }

  protected Web3j getWeb3j(ValidatorConfig validatorConfig) {
    return com.platon.mtool.common.web3j.Web3jUtil.getFromConfig(validatorConfig);
  }

  protected StakingContract getStakingContract(
      Web3j web3j, Credentials credentials) {
    return StakingContract.load(web3j, credentials);
  }

  @Override
  public void execute(UpdateValidatorOption option) throws Exception {
    LogUtils.info(
        logger, () -> Log.newBuilder().msg(AllCommands.UPDATE_VALIDATOR).kv("option", option));
    ProgressBar.start();

    if(option.getDelegateRewardPercent()==null &&
            option.getBenefitAddress()==null &&
            option.getDetails()==null &&
            option.getExternalId()==null &&
            option.getNodeName()==null &&
            option.getWebsite()==null ){
      throw new MtoolClientException("none option found");
    }

    ValidatorConfig validatorConfig = option.getConfig();
    Web3j web3j = getWeb3j(validatorConfig);

    Credentials credentials = option.getKeystore().getCredentials();
    StakingContract stakingContract = getStakingContract(web3j, credentials);

    // 参数
     UpdateStakingParam updateStakingParam =
        new UpdateStakingParam.Builder()
            .setNodeId(validatorConfig.getNodePublicKey())
            .setBenifitAddress(option.getBenefitAddress())
            .setExternalId(option.getExternalId())
            .setNodeName(option.getNodeName())
            .setWebSite(option.getWebsite())
            .setDetails(option.getDetails())
            .setRewardPer(option.getDelegateRewardPercent())
            .build();
    GasProvider gasProvider = checkGasPrice(stakingContract.getUpdateStakingInfoGasProvider(updateStakingParam));
    blockChainService.validBalanceEnough(option.getKeystore().getAddress(), BigInteger.ZERO, gasProvider, web3j, StakingAmountType.FREE_AMOUNT_TYPE);
    PlatonSendTransaction transaction = stakingContract.updateStakingInfoReturnTransaction(updateStakingParam, gasProvider).send();
    TransactionResponse response = stakingContract.getTransactionResponse(transaction).send();

    LogUtils.info(logger, () -> Log.newBuilder().msg(AllCommands.UPDATE_VALIDATOR).kv("transaction", transaction));
    LogUtils.info(logger, () -> Log.newBuilder().msg(AllCommands.UPDATE_VALIDATOR).kv("response", response));

    ProgressBar.stop();

    echoResult(
        transaction,
        response,
        validatorConfig.getNodePublicKey(),
        blockChainService.getCostAmount(null, gasProvider),
            blockChainService.getGovernParam(getWeb3j(validatorConfig),"staking","rewardPerMaxChangeRange"),
            blockChainService.getGovernParam(getWeb3j(validatorConfig),"staking","rewardPerChangeInterval"));
  }

  protected Node getStakingInfo(String nodeId, StakingContract stakingContract) throws Exception {
    CallResponse<Node> nodeBaseResponse = stakingContract.getStakingInfo(nodeId).send();
    if (!nodeBaseResponse.isStatusOk() || nodeBaseResponse.getData() == null) {
      throw new MtoolClientException("node not found");
    }
    return nodeBaseResponse.getData();
  }
}
