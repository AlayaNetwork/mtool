package com.platon.mtool.client.execute.restricting;

import com.beust.jcommander.JCommander;
import com.platon.contracts.ppos.RestrictingPlanContract;
import com.platon.contracts.ppos.dto.RestrictingPlan;
import com.platon.contracts.ppos.dto.TransactionResponse;
import com.platon.contracts.ppos.dto.enums.GovernParamItemSupported;
import com.platon.contracts.ppos.dto.enums.StakingAmountType;
import com.platon.contracts.ppos.dto.req.CreateRestrictingParam;
import com.platon.crypto.Credentials;
import com.platon.mtool.client.execute.MtoolExecutor;
import com.platon.mtool.client.options.restricting.CreateRestrictingPlanOption;
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
import com.platon.utils.Convert;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Arrays;

public class CreateRestrictingPlanExecutor extends MtoolExecutor<CreateRestrictingPlanOption> {

    private static final Logger logger = LoggerFactory.getLogger(CreateRestrictingPlanExecutor.class);

    private BlockChainService blockChainService = BlockChainService.singleton();

    public CreateRestrictingPlanExecutor(JCommander commander, CreateRestrictingPlanOption commonOption) {
        super(commander, commonOption);
    }

    protected Web3j getWeb3j(ValidatorConfig validatorConfig) {
        return com.platon.mtool.common.web3j.Web3jUtil.getFromConfig(validatorConfig);
    }

    public RestrictingPlanContract getRestrictingPlanContract(
            Web3j web3j, Credentials credentials) {
        return RestrictingPlanContract.load(web3j, credentials);
    }

    @Override
    public void execute(CreateRestrictingPlanOption option) throws Exception {
        LogUtils.info(logger, () -> Log.newBuilder().msg(AllCommands.CREATE_RESTRICTING).kv("option", option));
        ProgressBar.start();

        //?????????web3j
        ValidatorConfig validatorConfig = option.getConfig();
        Web3j web3j = getWeb3j(validatorConfig);

        //??????????????????????????????????????????????????????????????????????????????????????????
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
        //???????????????
        BigInteger totalVons = BigInteger.ZERO;
        if (option.getRestrictingConfig()!=null){
            for(RestrictingPlan plan : option.getRestrictingConfig().getPlans()){
                //????????????amoount????????????ATP
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


        //????????????JAVA?????????
        Credentials credentials = option.getKeystore().getCredentials();
        RestrictingPlanContract restrictingPlanContract = getRestrictingPlanContract(web3j, credentials);

        //?????????????????????????????????
        CreateRestrictingParam createRestrictingParam =  new CreateRestrictingParam();
        createRestrictingParam.setAccount(option.getRestrictingConfig().getAccount());
        createRestrictingParam.setPlans(option.getRestrictingConfig().getPlans());

        //??????gasLimit/gasPrice
        GasProvider gasProvider = restrictingPlanContract.getCreateRestrictingPlanGasProvider(createRestrictingParam);

        //??????????????????????????????????????????gasLimit*gasPrice)
        blockChainService.validBalanceEnough(option.getKeystore().getAddress(), totalVons, gasProvider, web3j, StakingAmountType.FREE_AMOUNT_TYPE);

        //??????????????????
        PlatonSendTransaction transaction = restrictingPlanContract.createRestrictingPlanReturnTransaction(createRestrictingParam.getAccount(), Arrays.asList(createRestrictingParam.getPlans()), gasProvider).send();

        //????????????
        TransactionResponse response = restrictingPlanContract.getTransactionResponse(transaction).send();

        LogUtils.info(logger, () -> Log.newBuilder().msg(AllCommands.CREATE_RESTRICTING).kv("transaction", transaction));
        LogUtils.info(logger, () -> Log.newBuilder().msg(AllCommands.CREATE_RESTRICTING).kv("response", response));

        ProgressBar.stop();
    }
}
