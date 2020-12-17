package com.platon.mtool.common.resolver;

import com.alaya.rlp.solidity.RlpDecoder;
import com.alaya.rlp.solidity.RlpList;
import com.alaya.rlp.solidity.RlpString;
import com.alaya.rlp.solidity.RlpType;
import com.platon.mtool.common.exception.MtoolException;
import java.math.BigInteger;
import java.util.List;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import com.alaya.utils.Numeric;

/** Created by liyf. */
public interface RlpResolverHelp {

  /**
   * rlp转BigInteger.
   *
   * @param rlpType RlpType
   * @return BigInteger
   */
  static BigInteger toBigInteger(RlpType rlpType) {
    RlpString rlpString = (RlpString) rlpType;
    RlpList rplList = RlpDecoder.decode(rlpString.getBytes());
    RlpString integerRlp = (RlpString) rplList.getValues().get(0);
    return new BigInteger(1, integerRlp.getBytes());
  }

  /**
   * rlp转String.
   *
   * @param rlpType RlpType
   * @return String
   */
  static String toString(RlpType rlpType) {
    RlpString rlpString = (RlpString) rlpType;
    RlpList rplList = RlpDecoder.decode(rlpString.getBytes());
    RlpString stringRlp = (RlpString) rplList.getValues().get(0);
    return Numeric.toHexString(stringRlp.getBytes());
  }

  /**
   * rlp转List.
   *
   * @param hexString String
   * @return List
   */
  static List<RlpType> toRlpTypeList(String hexString) {
    byte[] bytes;
    try {
      bytes = Hex.decodeHex(hexString);
    } catch (DecoderException e) {
      throw new MtoolException("rlp parse error", e);
    }
    RlpList rlpList = RlpDecoder.decode(bytes);
    List<RlpType> rlpTypes = rlpList.getValues();
    return ((RlpList) rlpTypes.get(0)).getValues();
  }
}
