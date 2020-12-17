package com.platon.mtool.common.logger;

import com.alibaba.fastjson.JSON;

import java.util.HashMap;
import java.util.Map;

/**
 * 日志建造者实现
 *
 * <p>Created by liyf.
 */
public class LogBuilderImpl implements Log.Builder {

  private String op;
  private String msg;
  private Map<String, Object> params = new HashMap<>();

  @Override
  public Log.Builder op(String op) {
    this.op = op;
    return this;
  }

  @Override
  public Log.Builder kv(String key, Object value) {
    if (value instanceof String) {
      params.put(key, value);
    } else {
      params.put(key, JSON.toJSONString(value));
    }
    return this;
  }

  @Override
  public Log.Builder msg(String msg) {
    this.msg = msg;
    return this;
  }

  @Override
  public Log build() {
    StringBuilder sb = new StringBuilder("### ");
    if (op != null) {
      sb.append("op: ").append(op).append("|");
    }
    if (msg != null) {
      sb.append("msg: ").append(msg).append("|");
    }
    if (!params.isEmpty()) {
      sb.append("kv: ").append(params).append("|");
    }
    return new Log(sb.toString());
  }
}
