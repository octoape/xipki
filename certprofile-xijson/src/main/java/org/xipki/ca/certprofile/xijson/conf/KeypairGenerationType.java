// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.certprofile.xijson.conf;

import org.xipki.ca.certprofile.xijson.conf.Describable.DescribableOid;
import org.xipki.security.EdECConstants;
import org.xipki.util.ValidatableConf;
import org.xipki.util.exception.InvalidConfException;

import java.util.Map;

/**
 * Configuration how the keypair is generated by CA.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public class KeypairGenerationType extends ValidatableConf {

  public static final String PARAM_keysize = "keysize";

  public static final String PARAM_curve = "curve";

  public static final String PARAM_plength = "plength";

  public static final String PARAM_qlength = "qlength";

  private Boolean inheritCA;

  private Boolean forbidden;

  private DescribableOid algorithm;

  private KeyType keyType;

  /**
   * The following properties will be evaluated.
   *
   * <ul>
   *   <li>For RSA key
   *     <ul>
   *       <li>keysize (required)</li>
   *     </ul>
   *   </li>
   *   <li>For EC key
   *     <ul>
   *       <li>curve (required)</li>
   *     </ul>
   *   </li>
   *   <li>For DSA key
   *     <ul>
   *       <li>plength (required)</li>
   *       <li>qlength (optional)</li>
   *     </ul>
   *   </li>
   *   <li>For Edwards and Montgomery key
   *     <ul>
   *       <li>no parameter</li>
   *     </ul>
   *   </li>
   * </ul>
   */
  private Map<String, String> parameters;

  public Boolean getInheritCA() {
    return inheritCA;
  }

  public void setInheritCA(Boolean inheritCA) {
    this.inheritCA = inheritCA;
  }

  public Boolean getForbidden() {
    return forbidden;
  }

  public void setForbidden(Boolean forbidden) {
    this.forbidden = forbidden;
  }

  public DescribableOid getAlgorithm() {
    return algorithm;
  }

  public void setAlgorithm(DescribableOid algorithm) {
    this.algorithm = algorithm;
  }

  public KeyType getKeyType() {
    return keyType;
  }

  public void setKeyType(KeyType keyType) {
    this.keyType = keyType;
  }

  public Map<String, String> getParameters() {
    return parameters;
  }

  public void setParameters(Map<String, String> parameters) {
    this.parameters = parameters == null || parameters.isEmpty() ? null : parameters;
  }

  @Override
  public void validate() throws InvalidConfException {
    if (inheritCA == null || inheritCA.booleanValue()) {
      return;
    }

    if (forbidden == null || forbidden.booleanValue()) {
      return;
    }

    notNull(algorithm, "algorithm");
    validate(algorithm);
    notNull(keyType, "keyType");

    switch (keyType) {
      case RSA:
        notNull(parameters, "parameters");
        if (!parameters.containsKey(PARAM_keysize)) {
          throw new InvalidConfException("parameters " + PARAM_keysize + " may not be null");
        }
        break;
      case DSA:
        notNull(parameters, "parameters");
        if (!parameters.containsKey(PARAM_plength)) {
          throw new InvalidConfException("parameters " + PARAM_plength + " may not be null");
        }
        break;
      case EC:
        notNull(parameters, "parameters");
        if (!parameters.containsKey(PARAM_curve)) {
          throw new InvalidConfException("parameters " + PARAM_curve + " may not be null");
        }
        break;
      case ED25519:
        if (!EdECConstants.id_ED25519.getId().equals(algorithm.getOid())) {
          throw new InvalidConfException("keyType and algorithm not match");
        }
        break;
      case ED448:
        if (!EdECConstants.id_ED448.getId().equals(algorithm.getOid())) {
          throw new InvalidConfException("keyType and algorithm not match");
        }
        break;
      case X25519:
        if (!EdECConstants.id_X25519.getId().equals(algorithm.getOid())) {
          throw new InvalidConfException("keyType and algorithm not match");
        }
        break;
      case X448:
        if (!EdECConstants.id_X448.getId().equals(algorithm.getOid())) {
          throw new InvalidConfException("keyType and algorithm not match");
        }
        break;
      default:
        break;
    }
  } // method validate

  public enum KeyType {
    RSA,
    EC,
    DSA,
    ED25519,
    ED448,
    X25519,
    X448
  } // class KeyType

}
