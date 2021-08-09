package com.platon.mtool.client.execute.observe;

import com.alibaba.fastjson.JSON;
import com.beust.jcommander.JCommander;
import com.platon.contracts.ppos.RestrictingPlanContract;
import com.platon.contracts.ppos.dto.RestrictingPlan;
import com.platon.contracts.ppos.dto.enums.GovernParamItemSupported;
import com.platon.contracts.ppos.dto.enums.StakingAmountType;
import com.platon.contracts.ppos.dto.req.CreateRestrictingParam;
import com.platon.mtool.client.ClientConsts;
import com.platon.mtool.client.execute.MtoolExecutor;
import com.platon.mtool.client.options.restricting.CreateRestrictingPlanOption;
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
import com.platon.utils.Convert;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;

import static com.platon.mtool.client.tools.CliConfigUtils.CLIENT_CONFIG;

public class ObserveCreateRestrictingPlanExecutor extends MtoolExecutor<CreateRestrictingPlanOption>  {

    private static final Logger logger = LoggerFactory.getLogger(ObserveCreateRestrictingPlanExecutor.class);
    private BlockChainService blockChainService = BlockChainService.singleton();

    public ObserveCreateRestrictingPlanExecutor(JCommander commander, CreateRestrictingPlanOption commonOption) {
        super(commander, commonOption);
    }

    @Override
    public void execute(CreateRestrictingPlanOption option) throws Exception {
        LogUtils.info(logger, () -> Log.newBuilder().msg("ObserveCreateRestrictingPlan").kv("option", option));
        ProgressBar.start();

        ValidatorConfig validatorConfig = option.getConfig();
        Web3j web3j = com.platon.mtool.common.web3j.Web3jUtil.getFromConfig(validatorConfig);

        //blockChainService.validSelfStakingAddress(web3j, validatorConfig.getNodePublicKey(), option.getKeystore().getAddress());

        String targetChainAddress = option.getKeystore().getAddress();

        //查询链上的治理参数值（创建锁仓计划时，每次释放金额的最小值）
        BigInteger minimumReleaseAtp = BigInteger.ZERO;
        String paramValue = blockChainService.getGovernParamValue(web3j, GovernParamItemSupported.Restricting_minimumRelease);
        if (StringUtils.isNotBlank(paramValue)) {
            minimumReleaseAtp = Convert.fromVon(paramValue, Convert.Unit.KPVON).toBigInteger();
            if(minimumReleaseAtp.signum()<=0){
                throw new MtoolClientException("invalid minimum amount of restricting release");
            }

        }else{
            throw new MtoolClientException("cannot find the minimum amount of restricting release");
        }

        //总锁仓金额
        BigInteger totalVons = BigInteger.ZERO;
        if (option.getRestrictingConfig()!=null){
            for(RestrictingPlan plan : option.getRestrictingConfig().getPlans()){
                //锁仓计划amoount的单位是ATP
                BigInteger atpAmount = plan.getAmount();
                BigInteger vonAmount = Convert.toVon(atpAmount.toString(), Convert.Unit.KPVON).toBigInteger();
                plan.setAmount(vonAmount);

                if (atpAmount.compareTo(minimumReleaseAtp)<0){
                    throw new MtoolClientException("plan item amount less than " + minimumReleaseAtp +"(ATP)");
                }
                totalVons = totalVons.add(vonAmount);
                /*if(plan.getEpoch().signum()<=0){
                    throw new MtoolClientException("plan item epoch is less than 1");
                }*/
            }
        }


        //设置TransactionManager = MtoolTransactionManager, 并不会真发送交易，而是返回交易数据对象TransactionEntity，用于生成待签名的交易数据的
        TransactionManager transactionManager = new MtoolTransactionManager(web3j, targetChainAddress);

        //用MtoolTransactionManager加载合约
        RestrictingPlanContract restrictingPlanContract = RestrictingPlanContract.load(web3j, transactionManager);

        //准备调用合约方法的参数
        CreateRestrictingParam createRestrictingParam =  new CreateRestrictingParam();
        createRestrictingParam.setAccount(option.getRestrictingConfig().getAccount());
        createRestrictingParam.setPlans(option.getRestrictingConfig().getPlans());

        //准备gasLimit/gasPrice
        GasProvider gasProvider = checkGasPrice(restrictingPlanContract.getCreateRestrictingPlanGasProvider(createRestrictingParam));

        //检查账户余额是否够付手续费（gasLimit*gasPrice)
        blockChainService.validBalanceEnough(option.getKeystore().getAddress(), totalVons, gasProvider, web3j, StakingAmountType.FREE_AMOUNT_TYPE);

        //有 MtoolTransactionManager 发送交易，由于MtoolTransactionManager重载了sendTransaction，只返回交易数据json，通过PlatonSendTransaction这个扩展Response的对象。
        PlatonSendTransaction transaction = restrictingPlanContract.createRestrictingPlanReturnTransaction(createRestrictingParam.getAccount(), Arrays.asList(createRestrictingParam.getPlans()), gasProvider).send();

        //获取交易数据json
        TransactionEntity entity = JSON.parseObject(transaction.getTransactionHash(), TransactionEntity.class);
        entity.setType(FuncTypeEnum.CREATE_RESTRICTING);
        entity.setAccountType("");
        entity.setAmount(totalVons);

        //把节点信息，以及命令行输入参数信息，存放到AdditionalInfo
        AdditionalInfo additionalInfo = new AdditionalInfo();
        BeanUtils.copyProperties(additionalInfo, validatorConfig);
        additionalInfo.setChainId(CLIENT_CONFIG.getChainId().toString());
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
