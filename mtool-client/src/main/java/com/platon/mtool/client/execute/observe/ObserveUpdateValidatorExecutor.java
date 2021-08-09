package com.platon.mtool.client.execute.observe;

import com.alibaba.fastjson.JSON;
import com.beust.jcommander.JCommander;
import com.platon.contracts.ppos.StakingContract;
import com.platon.contracts.ppos.dto.CallResponse;
import com.platon.contracts.ppos.dto.enums.StakingAmountType;
import com.platon.contracts.ppos.dto.req.UpdateStakingParam;
import com.platon.contracts.ppos.dto.resp.Node;
import com.platon.mtool.client.ClientConsts;
import com.platon.mtool.client.execute.MtoolExecutor;
import com.platon.mtool.client.options.UpdateValidatorOption;
import com.platon.mtool.client.service.BlockChainService;
import com.platon.mtool.client.tools.PrintUtils;
import com.platon.mtool.client.tools.ProgressBar;
import com.platon.mtool.client.tools.ResourceUtils;
import com.platon.mtool.common.entity.AdditionalInfo;
import com.platon.mtool.common.entity.ValidatorConfig;
import com.platon.mtool.common.enums.FuncTypeEnum;
import com.platon.mtool.common.exception.MtoolClientException;
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
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

import static com.platon.mtool.client.tools.CliConfigUtils.CLIENT_CONFIG;

/**
 * 观察钱包更新验证人
 *
 * <p>Created by liyf.
 */
public class ObserveUpdateValidatorExecutor extends MtoolExecutor<UpdateValidatorOption> {

  private static final Logger logger =
      LoggerFactory.getLogger(ObserveUpdateValidatorExecutor.class);
  private BlockChainService blockChainService = BlockChainService.singleton();

  public ObserveUpdateValidatorExecutor(JCommander commander, UpdateValidatorOption commonOption) {
    super(commander, commonOption);
  }

  @Override
  public void execute(UpdateValidatorOption option) throws Exception {
    LogUtils.info(logger, () -> Log.newBuilder().msg("updateValidator").kv("option", option));
    ProgressBar.start();

    ValidatorConfig validatorConfig = option.getConfig();
    String targetChainAddress = option.getKeystore().getAddress();

    Web3j web3j = com.platon.mtool.common.web3j.Web3jUtil.getFromConfig(validatorConfig);

    //设置TransactionManager = MtoolTransactionManager, 并不会真发送交易，而是返回交易数据对象TransactionEntity，用于生成待签名的交易数据的
    TransactionManager transactionManager =
        new MtoolTransactionManager(
            web3j, targetChainAddress);
    StakingContract stakingContract = StakingContract.load(web3j, transactionManager);

    blockChainService.validSelfStakingAddress(
        web3j, validatorConfig.getNodePublicKey(), option.getKeystore().getAddress());

    // 交易参数
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

    //检查账户是否够手续费
    GasProvider gasProvider = checkGasPrice(stakingContract.getUpdateStakingInfoGasProvider(updateStakingParam));
    blockChainService.validBalanceEnough(
        option.getKeystore().getAddress(), BigInteger.ZERO, gasProvider, web3j, StakingAmountType.FREE_AMOUNT_TYPE);

    //有 MtoolTransactionManager 发送交易，由于MtoolTransactionManager重载了sendTransaction，只返回交易数据json，通过PlatonSendTransaction这个扩展Response的对象。
    PlatonSendTransaction transaction =
        stakingContract.updateStakingInfoReturnTransaction(updateStakingParam, gasProvider).send();

    //获取交易数据json
    TransactionEntity entity =
        JSON.parseObject(transaction.getTransactionHash(), TransactionEntity.class);
    entity.setType(FuncTypeEnum.UPDATE_VALIDATOR);
    entity.setAccountType("");
    entity.setAmount(BigInteger.ZERO);

    //把节点信息，以及命令行输入参数信息，存放到AdditionalInfo
    AdditionalInfo additionalInfo = new AdditionalInfo();
    BeanUtils.copyProperties(additionalInfo, validatorConfig);
    additionalInfo.setChainId(CLIENT_CONFIG.getChainId().toString());
    additionalInfo.setNodeName(option.getNodeName());
    additionalInfo.setWebSite(option.getWebsite());

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

  private Node getStakingInfo(String nodeId, StakingContract stakingContract) throws Exception {
    CallResponse<Node> nodeBaseResponse = stakingContract.getStakingInfo(nodeId).send();
    if (!nodeBaseResponse.isStatusOk() || nodeBaseResponse.getData() == null) {
      throw new MtoolClientException("node not found");
    }
    return nodeBaseResponse.getData();
  }
}
