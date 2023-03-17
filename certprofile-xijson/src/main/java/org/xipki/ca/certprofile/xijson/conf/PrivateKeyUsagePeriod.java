// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.ca.certprofile.xijson.conf;

import org.xipki.util.ValidatableConf;
import org.xipki.util.exception.InvalidConfException;

/**
 * Extension PrivateKeyUsagePeriod.
 *
 * @author Lijun Liao (xipki)
 */

public class PrivateKeyUsagePeriod extends ValidatableConf {

  private String validity;

  public String getValidity() {
    return validity;
  }

  public void setValidity(String validity) {
    this.validity = validity;
  }

  @Override
  public void validate() throws InvalidConfException {
    notBlank(validity, "validity");
  }

} // class PrivateKeyUsagePeriod
