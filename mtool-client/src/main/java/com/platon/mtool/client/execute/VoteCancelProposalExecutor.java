package com.platon.mtool.client.execute;

import com.beust.jcommander.JCommander;
import com.platon.contracts.ppos.ProposalContract;
import com.platon.contracts.ppos.dto.TransactionResponse;
import com.platon.contracts.ppos.dto.common.ProposalType;
import com.platon.contracts.ppos.dto.enums.StakingAmountType;
import com.platon.crypto.Credentials;
import com.platon.mtool.client.options.VoteCancelProposalOption;
import com.platon.mtool.client.service.BlockChainService;
import com.platon.mtool.client.tools.ProgressBar;
import com.platon.mtool.common.entity.ValidatorConfig;
import com.platon.mtool.common.logger.Log;
import com.platon.mtool.common.utils.LogUtils;
import com.platon.protocol.Web3j;
import com.platon.protocol.core.methods.response.PlatonSendTransaction;
import com.platon.protocol.core.methods.response.bean.ProgramVersion;
import com.platon.tx.gas.GasProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/**
 * 取消提案投票
 *
 * <p>Created by liyf.
 */
public class VoteCancelProposalExecutor extends MtoolExecutor<VoteCancelProposalOption> {

  private static final Logger logger = LoggerFactory.getLogger(VoteCancelProposalExecutor.class);
  private BlockChainService blockChainService = BlockChainService.singleton();

  public VoteCancelProposalExecutor(JCommander commander, VoteCancelProposalOption commonOption) {
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
  public void execute(VoteCancelProposalOption option) throws Exception {
    LogUtils.info(logger, () -> Log.newBuilder().msg("VoteCancel").kv("option", option));
    ProgressBar.start();
    ValidatorConfig validatorConfig = option.getConfig();
    Web3j web3j = getWeb3j(validatorConfig);

    blockChainService.validVoteProposal(
        web3j, ProposalType.CANCEL_PROPOSAL, option.getProposalid());

    Credentials credentials = option.getKeystore().getCredentials();

    ProposalContract proposalContract =
        getProposalContract(web3j, credentials);

    ProgramVersion programVersion = web3j.getProgramVersion().send().getAdminProgramVersion();
    GasProvider gasProvider =
        checkGasPrice(proposalContract.getVoteProposalGasProvider(
                programVersion,
                option.getOpinion(),
                option.getProposalid(),
                validatorConfig.getNodePublicKey()));
    blockChainService.validBalanceEnough(
        option.getKeystore().getAddress(), BigInteger.ZERO, gasProvider, web3j, StakingAmountType.FREE_AMOUNT_TYPE);
    PlatonSendTransaction transaction =
        proposalContract
            .voteReturnTransaction(
                programVersion,
                option.getProposalid(),
                validatorConfig.getNodePublicKey(),
                option.getOpinion(),
                gasProvider)
            .send();

    TransactionResponse response = proposalContract.getTransactionResponse(transaction).send();

    LogUtils.info(logger, () -> Log.newBuilder().msg("VoteCancel").kv("transaction", transaction));
    LogUtils.info(logger, () -> Log.newBuilder().msg("VoteCancel").kv("response", response));
    ProgressBar.stop();
    echoResult(
        transaction,
        response,
        validatorConfig.getNodePublicKey(),
        blockChainService.getCostAmount(null, gasProvider));
  }
}
