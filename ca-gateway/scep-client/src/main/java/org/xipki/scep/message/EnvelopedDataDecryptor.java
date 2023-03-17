// Copyright (c) 2013-2023 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.scep.message;

import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JceKeyTransEnvelopedRecipient;
import org.xipki.security.X509Cert;
import org.xipki.util.Args;

import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * EnvelopedData decryptor.
 *
 * @author Lijun Liao (xipki)
 */

public final class EnvelopedDataDecryptor {

  public static final class EnvelopedDataDecryptorInstance {

    private final RecipientId recipientId;

    private final Recipient recipient;

    public EnvelopedDataDecryptorInstance(X509Cert recipientCert, PrivateKey privKey) {
      Args.notNull(recipientCert, "recipientCert");
      Args.notNull(privKey, "privKey");

      this.recipientId = new KeyTransRecipientId(
          recipientCert.getIssuer(), recipientCert.getSerialNumber(), recipientCert.getSubjectKeyId());
      this.recipient = new JceKeyTransEnvelopedRecipient(privKey);
    }

    public Recipient getRecipient() {
      return recipient;
    }

    public RecipientId getRecipientId() {
      return recipientId;
    }

  }

  private final List<EnvelopedDataDecryptorInstance> decryptors;

  public EnvelopedDataDecryptor(List<EnvelopedDataDecryptorInstance> decryptors) {
    this.decryptors = new ArrayList<>(Args.notEmpty(decryptors, "decryptors"));
  }

  public EnvelopedDataDecryptor(EnvelopedDataDecryptorInstance decryptor) {
    this.decryptors = Collections.singletonList(Args.notNull(decryptor, "decryptor"));
  }

  public byte[] decrypt(CMSEnvelopedData envData) throws MessageDecodingException {
    Args.notNull(envData, "envData");
    final RecipientInformationStore recipientInfos = envData.getRecipientInfos();
    RecipientInformation recipientInfo = null;
    EnvelopedDataDecryptorInstance decryptor = null;
    for (EnvelopedDataDecryptorInstance m : decryptors) {
      recipientInfo = recipientInfos.get(m.getRecipientId());
      if (recipientInfo != null) {
        decryptor = m;
        break;
      }
    }

    if (recipientInfo == null || decryptor == null) {
      throw new MessageDecodingException("missing expected key transfer recipient");
    }

    try {
      return recipientInfo.getContent(decryptor.getRecipient());
    } catch (CMSException ex) {
      throw new MessageDecodingException("could not decrypt the envelopedData");
    }
  }

}
