/*
 * Copyright (C) 2014-2016 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.opencsv.CSVReader;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigValue;

import gobblin.configuration.State;


/**
 * Utility class for dealing with {@link Config} objects.
 */
public class ConfigUtils {

  /**
   * List of keys that should be excluded when converting to typesafe config.
   * Usually, it is the key that is both the parent object of a value and a value, which is disallowed by Typesafe.
   */
  private static final String GOBBLIN_CONFIG_BLACKLIST_KEYS = "gobblin.config.blacklistKeys";

  /**
   * Convert a given {@link Config} instance to a {@link Properties} instance.
   *
   * @param config the given {@link Config} instance
   * @return a {@link Properties} instance
   */
  public static Properties configToProperties(Config config) {
    return configToProperties(config, Optional.<String>absent());
  }

  /**
   * Convert a given {@link Config} instance to a {@link Properties} instance.
   *
   * @param config the given {@link Config} instance
   * @param prefix an optional prefix; if present, only properties whose name starts with the prefix
   *        will be returned.
   * @return a {@link Properties} instance
   */
  public static Properties configToProperties(Config config, Optional<String> prefix) {
    Properties properties = new Properties();
    Config resolvedConfig = config.resolve();
    for (Map.Entry<String, ConfigValue> entry : resolvedConfig.entrySet()) {
      if (!prefix.isPresent() || entry.getKey().startsWith(prefix.get())) {
        properties.setProperty(entry.getKey(), resolvedConfig.getString(entry.getKey()));
      }
    }

    return properties;
  }

  /**
   * Convert a given {@link Config} instance to a {@link Properties} instance.
   *
   * @param config the given {@link Config} instance
   * @param prefix only properties whose name starts with the prefix will be returned.
   * @return a {@link Properties} instance
   */
  public static Properties configToProperties(Config config, String prefix) {
    return configToProperties(config, Optional.of(prefix));
  }

  /**
   * @return the subconfig under key "key" if it exists, otherwise returns an empty config.
   */
  public static Config getConfigOrEmpty(Config config, String key) {
    if (config.hasPath(key)) {
      return config.getConfig(key);
    } else {
      return ConfigFactory.empty();
    }
  }

  /**
   * Convert a given {@link Config} to a {@link State} instance.
   *
   * @param config the given {@link Config} instance
   * @return a {@link State} instance
   */
  public static State configToState(Config config) {
    return new State(configToProperties(config));
  }

  /**
   * Convert a given {@link Properties} to a {@link Config} instance.
   *
   * <p>
   *   This method will throw an exception if (1) the {@link Object#toString()} method of any two keys in the
   *   {@link Properties} objects returns the same {@link String}, or (2) if any two keys are prefixes of one another,
   *   see the Java Docs of {@link ConfigFactory#parseMap(Map)} for more details.
   * </p>
   *
   * @param properties the given {@link Properties} instance
   * @return a {@link Config} instance
   */
  public static Config propertiesToConfig(Properties properties) {
    return propertiesToConfig(properties, Optional.<String>absent());
  }

  /**
   * Convert all the keys that start with a <code>prefix</code> in {@link Properties} to a {@link Config} instance.
   *
   * <p>
   *   This method will throw an exception if (1) the {@link Object#toString()} method of any two keys in the
   *   {@link Properties} objects returns the same {@link String}, or (2) if any two keys are prefixes of one another,
   *   see the Java Docs of {@link ConfigFactory#parseMap(Map)} for more details.
   * </p>
   *
   * @param properties the given {@link Properties} instance
   * @param prefix of keys to be converted
   * @return a {@link Config} instance
   */
  public static Config propertiesToConfig(Properties properties, Optional<String> prefix) {
    Set<String> blacklistedKeys = new HashSet<>();
    if (properties.containsKey(GOBBLIN_CONFIG_BLACKLIST_KEYS)) {
      blacklistedKeys = new HashSet<>(Splitter.on(',').omitEmptyStrings().trimResults()
          .splitToList(properties.getProperty(GOBBLIN_CONFIG_BLACKLIST_KEYS)));
    }
    ImmutableMap.Builder<String, Object> immutableMapBuilder = ImmutableMap.builder();
    for (Map.Entry<Object, Object> entry : properties.entrySet()) {
      String entryKey = entry.getKey().toString();
      if (StringUtils.startsWith(entryKey, prefix.or(StringUtils.EMPTY)) && !blacklistedKeys.contains(entryKey)) {
        immutableMapBuilder.put(entryKey, entry.getValue());
      }
    }
    return ConfigFactory.parseMap(immutableMapBuilder.build());
  }

  /**
   * Convert all the keys that start with a <code>prefix</code> in {@link Properties} to a
   * {@link Config} instance. The method also tries to guess the types of properties.
   *
   * <p>
   *   This method will throw an exception if (1) the {@link Object#toString()} method of any two keys in the
   *   {@link Properties} objects returns the same {@link String}, or (2) if any two keys are prefixes of one another,
   *   see the Java Docs of {@link ConfigFactory#parseMap(Map)} for more details.
   * </p>
   *
   * @param properties the given {@link Properties} instance
   * @param prefix of keys to be converted
   * @return a {@link Config} instance
   */
  public static Config propertiesToTypedConfig(Properties properties, Optional<String> prefix) {
    Map<String, Object> typedProps = guessPropertiesTypes(properties);
    ImmutableMap.Builder<String, Object> immutableMapBuilder = ImmutableMap.builder();
    for (Map.Entry<String, Object> entry : typedProps.entrySet()) {
      if (StringUtils.startsWith(entry.getKey(), prefix.or(StringUtils.EMPTY))) {
        immutableMapBuilder.put(entry.getKey(), entry.getValue());
      }
    }
    return ConfigFactory.parseMap(immutableMapBuilder.build());
  }

  /** Attempts to guess type types of a Properties. By default, typesafe will make all property
   * values Strings. This implementation will try to recognize booleans and numbers. All keys are
   * treated as strings.*/
  private static Map<String, Object> guessPropertiesTypes(Map<Object, Object> srcProperties) {
    Map<String, Object> res = new HashMap<>();
    for (Map.Entry<Object, Object> prop : srcProperties.entrySet()) {
      Object value = prop.getValue();
      if (null != value && value instanceof String && !Strings.isNullOrEmpty(value.toString())) {
        try {
          value = Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
          try {
            value = Double.parseDouble(value.toString());
          } catch (NumberFormatException e2) {
            if (value.toString().equalsIgnoreCase("true") || value.toString().equalsIgnoreCase("yes")) {
              value = Boolean.TRUE;
            } else if (value.toString().equalsIgnoreCase("false") || value.toString().equalsIgnoreCase("no")) {
              value = Boolean.FALSE;
            } else {
              // nothing to do
            }
          }
        }
      }
      res.put(prop.getKey().toString(), value);
    }
    return res;
  }

  /**
   * Return string value at <code>path</code> if <code>config</code> has path. If not return an empty string
   *
   * @param config in which the path may be present
   * @param path key to look for in the config object
   * @return string value at <code>path</code> if <code>config</code> has path. If not return an empty string
   */
  public static String emptyIfNotPresent(Config config, String path) {
    return getString(config, path, StringUtils.EMPTY);
  }

  /**
   * Return string value at <code>path</code> if <code>config</code> has path. If not return <code>def</code>
   *
   * @param config in which the path may be present
   * @param path key to look for in the config object
   * @return string value at <code>path</code> if <code>config</code> has path. If not return <code>def</code>
   */
  public static String getString(Config config, String path, String def) {
    if (config.hasPath(path)) {
      return config.getString(path);
    }
    return def;
  }

  /**
   * Return {@link Long} value at <code>path</code> if <code>config</code> has path. If not return <code>def</code>
   *
   * @param config in which the path may be present
   * @param path key to look for in the config object
   * @return {@link Long} value at <code>path</code> if <code>config</code> has path. If not return <code>def</code>
   */
  public static Long getLong(Config config, String path, Long def) {
    if (config.hasPath(path)) {
      return Long.valueOf(config.getLong(path));
    }
    return def;
  }

  /**
   * Return {@link Integer} value at <code>path</code> if <code>config</code> has path. If not return <code>def</code>
   *
   * @param config in which the path may be present
   * @param path key to look for in the config object
   * @return {@link Integer} value at <code>path</code> if <code>config</code> has path. If not return <code>def</code>
   */
  public static Integer getInt(Config config, String path, Integer def) {
    if (config.hasPath(path)) {
      return Integer.valueOf(config.getInt(path));
    }
    return def;
  }

  /**
   * Return boolean value at <code>path</code> if <code>config</code> has path. If not return <code>def</code>
   *
   * @param config in which the path may be present
   * @param path key to look for in the config object
   * @return boolean value at <code>path</code> if <code>config</code> has path. If not return <code>def</code>
   */
  public static boolean getBoolean(Config config, String path, boolean def) {
    if (config.hasPath(path)) {
      return config.getBoolean(path);
    }
    return def;
  }

  /**
   * Return double value at <code>path</code> if <code>config</code> has path. If not return <code>def</code>
   *
   * @param config in which the path may be present
   * @param path key to look for in the config object
   * @return double value at <code>path</code> if <code>config</code> has path. If not return <code>def</code>
   */
  public static double getDouble(Config config, String path, double def) {
    if (config.hasPath(path)) {
      return config.getDouble(path);
    }
    return def;
  }

  /**
   * Return {@link Config} value at <code>path</code> if <code>config</code> has path. If not return <code>def</code>
   *
   * @param config in which the path may be present
   * @param path key to look for in the config object
   * @return config value at <code>path</code> if <code>config</code> has path. If not return <code>def</code>
   */
  public static Config getConfig(Config config, String path, Config def) {
    if (config.hasPath(path)) {
      return config.getConfig(path);
    }
    return def;
  }

  /**
   * <p>
   * An extension to {@link Config#getStringList(String)}. The value at <code>path</code> can either be a TypeSafe
   * {@link ConfigList} of strings in which case it delegates to {@link Config#getStringList(String)} or as list of
   * comma separated strings in which case it splits the comma separated list.
   *
   *
   * </p>
   * Additionally
   * <li>Returns an empty list if <code>path</code> does not exist
   * <li>removes any leading and lagging quotes from each string in the returned list.
   *
   * Examples below will all return a list [1,2,3] without quotes
   *
   * <ul>
   * <li> a.b=[1,2,3]
   * <li> a.b=["1","2","3"]
   * <li> a.b=1,2,3
   * <li> a.b="1","2","3"
   * </ul>
   *
   * @param config in which the path may be present
   * @param path key to look for in the config object
   * @return list of strings
   */
  public static List<String> getStringList(Config config, String path) {

    if (!config.hasPath(path)) {
      return Collections.emptyList();
    }

    List<String> valueList = Lists.newArrayList();
    try {
      valueList = config.getStringList(path);
    } catch (ConfigException.WrongType e) {

      /*
       * Using CSV Reader as values could be quoted.
       * E.g The string "a","false","b","10,12" will be split to a list of 4 elements and not 5.
       *
       * a
       * false
       * b
       * 10,12
       */
      try (CSVReader csvr = new CSVReader(new StringReader(config.getString(path)));) {
        valueList = Lists.newArrayList(csvr.readNext());
      } catch (IOException ioe) {
        throw new RuntimeException(ioe);
      }
    }

    // Remove any leading or lagging quotes in the values
    // [\"a\",\"b\"] ---> [a,b]
    return Lists.newArrayList(Lists.transform(valueList, new Function<String, String>() {
      @Override
      public String apply(String input) {
        if (input == null) {
          return input;
        }
        return input.replaceAll("^\"|\"$", "");
      }
    }));
  }

  /**
   * Check if the given <code>key</code> exists in <code>config</code> and it is not null or empty
   * Uses {@link StringUtils#isNotBlank(CharSequence)}
   * @param config which may have the key
   * @param key to look for in the config
   *
   * @return True if key exits and not null or empty. False otherwise
   */
  public static boolean hasNonEmptyPath(Config config, String key) {
    return config.hasPath(key) && StringUtils.isNotBlank(config.getString(key));
  }

  /**
   * Check that every key-value in superConfig is in subConfig
   */
  public static boolean verifySubset(Config superConfig, Config subConfig) {
    for (Map.Entry<String, ConfigValue> entry : subConfig.entrySet()) {
      if (!superConfig.hasPath(entry.getKey()) || !superConfig.getValue(entry.getKey()).unwrapped()
          .equals(entry.getValue().unwrapped())) {
        return false;
      }
    }
    return true;
  }
}
