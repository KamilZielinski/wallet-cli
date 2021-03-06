package org.tron.walletcli;

import java.util.HashMap;
import java.util.Optional;

import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;
import org.tron.api.GrpcAPI.AccountList;
import org.tron.api.GrpcAPI.WitnessList;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.SymmEncoder;
import org.tron.common.utils.ByteArray;
import org.tron.protos.Contract;
import org.tron.protos.Protocol;
import org.tron.walletserver.WalletClient;

public class Client {

  private static final Logger logger = LoggerFactory.getLogger("Client");
  private WalletClient wallet;

  public boolean registerWallet(String userName, String password) {
    if (!WalletClient.passwordValid(password)) {
      return false;
    }
    wallet = new WalletClient(true);
    // create account at network
    Boolean ret = wallet.createAccount(Protocol.AccountType.Normal, userName.getBytes());
    if (ret) {
      wallet.store(password);
    }
    return ret;
  }

  public boolean importWallet(String password, String priKey) {
    if (!WalletClient.passwordValid(password)) {
      return false;
    }
    if (!WalletClient.priKeyValid(priKey)) {
      return false;
    }
    wallet = new WalletClient(priKey);
    if (wallet.getEcKey() == null) {
      return false;
    }
    wallet.store(password);
    return true;
  }

  public boolean changePassword(String oldPassword, String newPassword) {
    if (wallet == null || !wallet.isLoginState()) {
      logger.warn("Warning: ChangePassword failed, Please login first !!");
      return false;
    }
    if (!WalletClient.passwordValid(oldPassword)) {
      logger.warn("Warning: ChangePassword failed, OldPassword is invalid !!");
      return false;
    }
    if (!WalletClient.passwordValid(newPassword)) {
      logger.warn("Warning: ChangePassword failed, NewPassword is invalid !!");
      return false;
    }
    if (!WalletClient.checkPassWord(oldPassword)) {
      logger
          .warn(
              "Warning: ChangePassword failed, Wrong password !!");
      return false;
    }

    if (wallet.getEcKey() == null || wallet.getEcKey().getPrivKey() == null) {
      wallet = WalletClient.GetWalletByStorage(oldPassword);
      if (wallet == null) {
        logger
            .warn("Warning: ChangePassword failed, No wallet !!");
        return false;
      }
    }
    byte[] priKeyAsc = wallet.getEcKey().getPrivKeyBytes();
    String priKey = Hex.toHexString(priKeyAsc, 0, priKeyAsc.length);
    return importWallet(newPassword, priKey);
  }

  public boolean login(String password) {
    if (!WalletClient.passwordValid(password)) {
      return false;
    }
    if (wallet == null) {
      wallet = WalletClient.GetWalletByStorage(password);
      if (wallet == null) {
        logger
            .warn("Warning: Login failed, Please registerWallet or importWallet first !!");
        return false;
      }
    }
    return wallet.login(password);
  }

  public void logout() {
    if (wallet != null) {
      wallet.logout();
    }
    //Neddn't logout
  }

  //password is current, will be enc by password2.
  public String backupWallet(String password, String encPassword) {
    if (wallet == null || !wallet.isLoginState()) {
      logger.warn("Warning: BackupWallet failed, Please login first !!");
      return null;
    }
    if (!WalletClient.passwordValid(password)) {
      logger.warn("Warning: BackupWallet failed, password is Invalid !!");
      return null;
    }
    if (!WalletClient.passwordValid(encPassword)) {
      logger.warn("Warning: BackupWallet failed, encPassword is Invalid !!");
      return null;
    }

    if (!WalletClient.checkPassWord(password)) {
      logger
          .warn(
              "Warning: BackupWallet failed, Wrong password !!");
      return null;
    }

    if (wallet.getEcKey() == null || wallet.getEcKey().getPrivKey() == null) {
      wallet = WalletClient.GetWalletByStorage(password);
      if (wallet == null) {
        logger
            .warn(
                "Warning: BackupWallet failed, no wallet can be backup !!");
        return null;
      }
    }
    ECKey ecKey = wallet.getEcKey();
    byte[] privKeyPlain = ecKey.getPrivKeyBytes();
    //Enced by encPassword
    byte[] aseKey = WalletClient.getEncKey(encPassword);
    byte[] privKeyEnced = SymmEncoder.AES128EcbEnc(privKeyPlain, aseKey);
    String priKey = ByteArray.toHexString(privKeyEnced);

    return priKey;
  }

  public String getAddress() {
    if (wallet == null || !wallet.isLoginState()) {
      logger.warn("Warning: GetAddress failed,  Please login first !!");
      return null;
    }

    if (wallet.getEcKey() == null) {
      return WalletClient.getAddressByStorage();
    }
    return ByteArray.toHexString(wallet.getAddress());
  }

  public long getBalance() {
    if (wallet == null || !wallet.isLoginState()) {
      logger.warn("Warning: GetBalance failed,  Please login first !!");
      return 0;
    }

    if (wallet.getEcKey() == null) {
      wallet = WalletClient.GetWalletByStorageIgnorPrivKey();
      if (wallet == null) {
        logger.warn("Warning: GetBalance failed, Load wallet failed !!");
        return 0;
      }
    }

    try {
      return wallet.getBalance();
    } catch (Exception ex) {
      ex.printStackTrace();
      return 0;
    }
  }

  public boolean sendCoin(String password, String toAddress, long amount) {
    if (wallet == null || !wallet.isLoginState()) {
      logger.warn("Warning: SendCoin failed,  Please login first !!");
      return false;
    }
    if (!WalletClient.passwordValid(password)) {
      return false;
    }
    if (!WalletClient.addressValid(toAddress)) {
      return false;
    }

    if (wallet.getEcKey() == null || wallet.getEcKey().getPrivKey() == null) {
      wallet = WalletClient.GetWalletByStorage(password);
      if (wallet == null) {
        logger.warn("Warning: SendCoin failed, Load wallet failed !!");
        return false;
      }
    }

    try {
      byte[] to = Hex.decode(toAddress);
      return wallet.sendCoin(to, amount);
    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
  }

  public boolean assetIssue(String password, String name, long totalSupply, int trxNum, int icoNum,
      long startTime, long endTime, int decayRatio, int voteScore, String description, String url) {
    if (wallet == null || !wallet.isLoginState()) {
      logger.warn("Warning: assetIssue failed,  Please login first !!");
      return false;
    }
    if (!WalletClient.passwordValid(password)) {
      return false;
    }

    if (wallet.getEcKey() == null || wallet.getEcKey().getPrivKey() == null) {
      wallet = WalletClient.GetWalletByStorage(password);
      if (wallet == null) {
        logger.warn("Warning: assetIssue failed, Load wallet failed !!");
        return false;
      }
    }

    try {
      Contract.AssetIssueContract.Builder builder = Contract.AssetIssueContract.newBuilder();
      builder.setOwnerAddress(ByteString.copyFrom(wallet.getAddress()));
      builder.setName(ByteString.copyFrom(name.getBytes()));
      if (totalSupply <= 0) {
        return false;
      }
      builder.setTotalSupply(totalSupply);
      if (trxNum <= 0) {
        return false;
      }
      builder.setTrxNum(trxNum);
      if (icoNum <= 0) {
        return false;
      }
      builder.setNum(icoNum);
      long now = System.currentTimeMillis();
      if (startTime <= now) {
        return false;
      }
      if (endTime <= startTime) {
        return false;
      }
      builder.setStartTime(startTime);
      builder.setEndTime(endTime);
      builder.setDecayRatio(decayRatio);
      builder.setVoteScore(voteScore);
      builder.setDescription(ByteString.copyFrom(description.getBytes()));
      builder.setUrl(ByteString.copyFrom(url.getBytes()));

      return wallet.createAssetIssue(builder.build());
    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
  }

  public boolean createWitness(String password, String url) {
    if (wallet == null || !wallet.isLoginState()) {
      logger.warn("Warning: createWitness failed,  Please login first !!");
      return false;
    }
    if (!WalletClient.passwordValid(password)) {
      return false;
    }

    if (wallet.getEcKey() == null || wallet.getEcKey().getPrivKey() == null) {
      wallet = WalletClient.GetWalletByStorage(password);
      if (wallet == null) {
        logger.warn("Warning: createWitness failed, Load wallet failed !!");
        return false;
      }
    }

    try {
      return wallet.createWitness(url.getBytes());
    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
  }

  public boolean voteWitness(String password, HashMap<String, String> witness) {
    if (wallet == null || !wallet.isLoginState()) {
      logger.warn("Warning: SendCoin failed,  Please login first !!");
      return false;
    }
    if (!WalletClient.passwordValid(password)) {
      return false;
    }

    if (wallet.getEcKey() == null || wallet.getEcKey().getPrivKey() == null) {
      wallet = WalletClient.GetWalletByStorage(password);
      if (wallet == null) {
        logger.warn("Warning: SendCoin failed, Load wallet failed !!");
        return false;
      }
    }

    try {
      return wallet.voteWitness(witness);
    } catch (Exception ex) {
      ex.printStackTrace();
      return false;
    }
  }

  public Optional<AccountList> listAccounts() {
    if (wallet == null) {
      logger.error("Wallet is null");
      return Optional.empty();
    }

    try {
      return wallet.listAccounts();
    } catch (Exception ex) {
      ex.printStackTrace();
      return Optional.empty();
    }
  }

  public Optional<WitnessList> listWitnesses() {
    if (wallet == null) {
      logger.error("Wallet is null");
      return Optional.empty();
    }

    try {
      return wallet.listWitnesses();
    } catch (Exception ex) {
      ex.printStackTrace();
      return Optional.empty();
    }
  }
}
