package com.platon.mtool.client.execute;

import com.beust.jcommander.JCommander;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platon.crypto.Credentials;
import com.platon.crypto.TransactionEncoder;
import com.platon.crypto.WalletFile;
import com.platon.crypto.WalletUtils;
import com.platon.mtool.client.ClientConsts;
import com.platon.mtool.client.converter.KeystoreConverter;
import com.platon.mtool.client.options.OfflineSignOption;
import com.platon.mtool.client.tools.PrintUtils;
import com.platon.mtool.client.tools.ProgressBar;
import com.platon.mtool.client.tools.ResourceUtils;
import com.platon.mtool.common.AllParams;
import com.platon.mtool.common.exception.MtoolClientException;
import com.platon.mtool.common.logger.Log;
import com.platon.mtool.common.utils.HashUtil;
import com.platon.mtool.common.utils.LogUtils;
import com.platon.mtool.common.utils.MtoolCsvFileUtil;
import com.platon.mtool.common.utils.PlatOnUnit;
import com.platon.mtool.common.web3j.Keystore;
import com.platon.mtool.common.web3j.TransactionEntity;
import com.platon.utils.Numeric;
import de.vandermeer.asciitable.AsciiTable;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * 离线签名
 *
 * <p>Created by liyf.
 */
public class OfflineSignExcutor extends MtoolExecutor<OfflineSignOption> {

    private static final Logger logger = LoggerFactory.getLogger(OfflineSignExcutor.class);

    public OfflineSignExcutor(JCommander commander, OfflineSignOption commonOption) {
        super(commander, commonOption);
    }

    @Override
    public void execute(OfflineSignOption option) throws Exception {
        LogUtils.info(logger, () -> Log.newBuilder().msg("OfflineSign").kv("option", option));
        // 统计分析数量
        int successNum = 0; // 成功数
        int failureNum = 0; // 失败数
        int totalNum; // 总数量
        int toBeSignedNum; // 有效数量
        Map<String /*address*/, Keystore /*keystore*/> keystoreMap = new HashMap<>();
        // 1. 把文件解析成交易
        List<TransactionEntity> entities =
                MtoolCsvFileUtil.readTransactionDetailFromFile(
                        option.getFilelocation().toAbsolutePath().toString());

        if (entities.isEmpty()) {
            throw new MtoolClientException("Warning: There is no transfer information to sign");
        }

        for (TransactionEntity entity : entities) {
            if (!HashUtil.isTransactionValid(entity)) {
                throw new MtoolClientException("Tampered tx, please re-check your file.");
            }
        }

        totalNum = entities.size();
        // 2. 过滤已签名数据
        entities =
                entities.stream()
                        .filter(transactionEntity -> StringUtils.isEmpty(transactionEntity.getSignData()))
                        .collect(Collectors.toList());
        toBeSignedNum = entities.size();

        // 全部签名不生成新文件， 直接成功
        if (toBeSignedNum == 0) {
            PrintUtils.echo("Signed Transaction");
            return;
        }

        // 3. 查找交易涉及的钱包
        Set<String> addressSet = new HashSet<>();
        for (TransactionEntity entity : entities) {
            addressSet.add(entity.getFrom());
        }
        echoTransaction(entities);

        LogUtils.info(logger, () -> Log.newBuilder().kv("address set", addressSet));
        PrintUtils.echo("Need load %s wallets for address: %s", addressSet.size(), addressSet);
        // 4. 查找文件夹所有钱包， 匹配交易钱包， 依次输入钱包密码
        List<Path> keystorePaths;
        try (Stream<Path> paths = Files.list(ResourceUtils.getKeystorePath())) {
            keystorePaths = paths.collect(Collectors.toList());
        }
        KeystoreConverter converter = new KeystoreConverter(AllParams.KEYSTORE);
        for (Path keystorePath : keystorePaths) {
            if(keystorePath.getFileName().toString().contains("observed")){
                // 忽略观察钱包
                continue;
            }
            //String address = getAddress(keystorePath);
            WalletFile walletFile = WalletUtils.loadWalletFile(keystorePath.toFile());
            String address = walletFile.getAddress();

            if (address == null) continue;
            if (addressSet.contains(address)) {
                Keystore keystore = new Keystore();
                keystore.setFilepath(keystorePath.toAbsolutePath().toString());
                keystoreMap.put(address, keystore);
            }
        }
        if (addressSet.size() != keystoreMap.size()) {
            addressSet.removeAll(keystoreMap.keySet());
            throw new MtoolClientException("Cold wallet for address not found: %s", addressSet);
        }
        keystoreMap.forEach(
                (address, keystore) -> {
                    PrintUtils.echo(
                            "Input passowrd for wallet please: %s",
                            FilenameUtils.getName(keystore.getFilepath()));
                    Credentials credentials = converter.convert(keystore.getFilepath()).getCredentials();
                    keystore.setCredentials(credentials);
                });

        ProgressBar.start();
        // 5. 交易签名， 记录成功数量和失败数量
        for (TransactionEntity entity : entities) {
            Credentials credentials = keystoreMap.get(entity.getFrom()).getCredentials();
            try {
                byte[] signedMessage =
                        TransactionEncoder.signMessage(entity, entity.getChainId(), credentials);
                String hexValue = Numeric.toHexString(signedMessage);
                entity.setSignData(hexValue);
                entity.setSignTime(LocalDateTime.now());
                successNum += 1;
            } catch (Exception e) {
                LogUtils.info(logger, () -> Log.newBuilder().msg("sign fail").kv("entity", entity));
                failureNum += 1;
            }
        }

        // 6. 返回签完名文件， 打印成功和失败数量
        byte[] bytes = MtoolCsvFileUtil.toTransactionDetailBytes(entities);
        String filename =
                String.format(
                        "transaction_signature_%s.csv",
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
        String filepath =
                ResourceUtils.getTransactionSignaturePath().resolve(filename).toAbsolutePath().toString();
        FileUtils.writeByteArrayToFile(new File(filepath), bytes);
        ProgressBar.stop();
        PrintUtils.echo(ClientConsts.SUCCESS);
        PrintUtils.echo("File generated on %s", filepath);
        PrintUtils.echo("total: %s, to be signed: %s", totalNum, toBeSignedNum);
        PrintUtils.echo("success: %s, failure: %s", successNum, failureNum);
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String getAddress(Path keystorePath) {
        try {
            Keystore keystore = Keystore.loadKeystore(keystorePath.toAbsolutePath().toString());
            if (keystore == null) {
                LogUtils.info(logger, () -> Log.newBuilder().msg("wallet format error!").kv("name", keystorePath));
                return null;
            }
            if (keystore.getType() == Keystore.Type.OBSERVE) return null;
            return keystore.getAddress();
        } catch (Exception e) {
            LogUtils.info(logger, () -> Log.newBuilder().msg("wallet format error!").kv("name", keystorePath));
            return null;
        }
   /* ObservedWalletFile walletFile;
    try {
      walletFile = objectMapper.readValue(keystorePath.toFile(), ObservedWalletFile.class);
      // 观察钱包不能做签名
      if(walletFile.getType()== Keystore.Type.OBSERVE) return null;
    } catch (Exception e) {
      LogUtils.info(
          logger, () -> Log.newBuilder().msg("wallet format error!").kv("name", keystorePath));
      return null;
    }
    return walletFile.getAddress();*/
    }

    private void echoTransaction(List<TransactionEntity> entityList) {
        AsciiTable at = new AsciiTable();
        at.addRule();
        at.addRow(
                "Type", "From", "To", "Account Type", "Amount", "Fee", "Nonce", "Create Time", "Chain Id");
        at.addRule();
        for (TransactionEntity entity : entityList) {
            at.addRow(
                    entity.getType(),
                    entity.getFrom(),
                    entity.getTo(),
                    entity.getAccountType(),
                    PlatOnUnit.vonToLat(entity.getAmount()),
                    PlatOnUnit.vonToLat(entity.getFee()),
                    entity.getNonce(),
                    entity.getCreateTime(),
                    entity.getChainId());
            at.addRule();
        }
        String rend = at.render();
        PrintUtils.echo(rend);
    }
}
