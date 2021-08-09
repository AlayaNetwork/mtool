package com.platon.mtool.client.execute.observe;

import com.alibaba.fastjson.JSON;
import com.beust.jcommander.JCommander;
import com.platon.contracts.ppos.StakingContract;
import com.platon.contracts.ppos.dto.req.StakingParam;
import com.platon.mtool.client.ClientConsts;
import com.platon.mtool.client.execute.MtoolExecutor;
import com.platon.mtool.client.options.StakingOption;
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
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * 观察钱包质押
 *
 * <p>Created by liyf.
 */
public class ObserveStakingExecutor extends MtoolExecutor<StakingOption> {

  private static final Logger logger = LoggerFactory.getLogger(ObserveStakingExecutor.class);
  private BlockChainService blockChainService = BlockChainService.singleton();

  public ObserveStakingExecutor(JCommander commander, StakingOption commonOption) {
    super(commander, commonOption);
  }

  @Override
  public void execute(StakingOption option) throws Exception {
    LogUtils.info(logger, () -> Log.newBuilder().msg("ObserveStaking").kv("option", option));
    ProgressBar.start();

    ValidatorConfig validatorConfig = option.getConfig();
    Web3j web3j = com.platon.mtool.common.web3j.Web3jUtil.getFromConfig(validatorConfig);
    // 检查当前金额是否满足链上的最小质押金额
    blockChainService.validAmount(web3j,"staking","stakeThreshold",option.getAmount().getAmount());

    //blockChainService.validSelfStakingAddress(web3j, validatorConfig.getNodePublicKey(), option.getKeystore().getAddress());

    String targetChainAddress = option.getKeystore().getAddress();

    //设置TransactionManager = MtoolTransactionManager, 并不会真发送交易，而是返回交易数据对象TransactionEntity，用于生成待签名的交易数据的
    TransactionManager transactionManager =
        new MtoolTransactionManager(
            web3j, targetChainAddress);
    StakingContract stakingContract = StakingContract.load(web3j, transactionManager);

    // 交易参数
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

    //检查账户是否够余额
    GasProvider gasProvider = checkGasPrice(stakingContract.getStakingGasProvider(stakingParam));
    blockChainService.validBalanceEnough(
        option.getKeystore().getAddress(), option.getAmount().getAmount(), gasProvider, web3j,option.getAmount().getAmountType());

    //有 MtoolTransactionManager 发送交易，由于MtoolTransactionManager重载了sendTransaction，只返回交易数据json，通过PlatonSendTransaction这个扩展Response的对象。
    PlatonSendTransaction transaction =
        stakingContract.stakingReturnTransaction(stakingParam, gasProvider).send();

    //获取交易数据json
    TransactionEntity entity =
        JSON.parseObject(transaction.getTransactionHash(), TransactionEntity.class);
    entity.setType(FuncTypeEnum.STAKING);
    entity.setAccountType(option.getAmount().getAmountType().name());
    entity.setAmount(option.getAmount().getAmount());

    //把节点信息，以及命令行输入参数信息，存放到AdditionalInfo
    AdditionalInfo additionalInfo = new AdditionalInfo();
    BeanUtils.copyProperties(additionalInfo, validatorConfig);
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
}
