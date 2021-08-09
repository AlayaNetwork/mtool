package com.platon.mtool.client.execute;

import com.beust.jcommander.JCommander;
import com.platon.contracts.ppos.ProposalContract;
import com.platon.contracts.ppos.dto.BaseResponse;
import com.platon.contracts.ppos.dto.enums.StakingAmountType;
import com.platon.contracts.ppos.dto.resp.Proposal;
import com.platon.crypto.Credentials;
import com.platon.mtool.client.options.SubmitCancelProposalOption;
import com.platon.mtool.client.service.BlockChainService;
import com.platon.mtool.client.tools.ProgressBar;
import com.platon.mtool.common.entity.ValidatorConfig;
import com.platon.mtool.common.logger.Log;
import com.platon.mtool.common.utils.LogUtils;
import com.platon.protocol.Web3j;
import com.platon.protocol.core.methods.response.PlatonSendTransaction;
import com.platon.tx.gas.GasProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/**
 * 提交取消提案
 *
 * <p>Created by liyf.
 */
public class SubmitCancelProposalExecutor extends MtoolExecutor<SubmitCancelProposalOption> {

  private static final Logger logger = LoggerFactory.getLogger(SubmitCancelProposalExecutor.class);
  private BlockChainService blockChainService = BlockChainService.singleton();

  public SubmitCancelProposalExecutor(
      JCommander commander, SubmitCancelProposalOption commonOption) {
    super(commander, commonOption);
  }

  protected Web3j getWeb3j(ValidatorConfig validatorConfig) {
    return com.platon.mtool.common.web3j.Web3jUtil.getFromConfig(validatorConfig);
  }

  protected ProposalContract getProposalContract(
      Web3j web3j, Credentials credentials) {
    return ProposalContract.load(web3j, credentials);
  }

  @Override
  public void execute(SubmitCancelProposalOption option) throws Exception {
    LogUtils.info(logger, () -> Log.newBuilder().msg("SubmitCancelproposal").kv("option", option));
    ProgressBar.start();
    ValidatorConfig validatorConfig = option.getConfig();
    Web3j web3j = getWeb3j(validatorConfig);

    blockChainService.validCancelProposal(web3j, option.getProposalid());

    Credentials credentials = option.getKeystore().getCredentials();
    ProposalContract proposalContract =
        getProposalContract(web3j, credentials);

    Proposal proposal =
        Proposal.createSubmitCancelProposalParam(
            validatorConfig.getNodePublicKey(),
            option.getPidId(),
            option.getEndVotingRounds(),
            option.getProposalid());
    GasProvider gasProvider = checkGasPrice(proposalContract.getSubmitProposalGasProvider(proposal));
    blockChainService.validBalanceEnough(
        option.getKeystore().getAddress(), BigInteger.ZERO, gasProvider, web3j, StakingAmountType.FREE_AMOUNT_TYPE);
    PlatonSendTransaction transaction =
        proposalContract.submitProposalReturnTransaction(proposal, gasProvider).send();
    BaseResponse response = proposalContract.getTransactionResponse(transaction).send();

    LogUtils.info(logger, () -> Log.newBuilder().msg("SubmitCancelproposal").kv("transaction", transaction));
    LogUtils.info(logger, () -> Log.newBuilder().msg("SubmitCancelproposal").kv("response", response));

    ProgressBar.stop();
    echoResult(
        transaction,
        response,
        validatorConfig.getNodePublicKey(),
        blockChainService.getCostAmount(null, gasProvider));
  }
}
