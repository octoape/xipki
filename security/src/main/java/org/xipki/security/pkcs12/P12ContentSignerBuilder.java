// Copyright (c) 2013-2024 xipki. All rights reserved.
// License Apache License 2.0

package org.xipki.security.pkcs12;

import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.Signer;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.signers.DSADigestSigner;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.RSADigestSigner;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcContentSignerBuilder;
import org.xipki.security.ConcurrentContentSigner;
import org.xipki.security.DfltConcurrentContentSigner;
import org.xipki.security.SignAlgo;
import org.xipki.security.SignatureSigner;
import org.xipki.security.X509Cert;
import org.xipki.security.XiContentSigner;
import org.xipki.security.XiSecurityException;
import org.xipki.security.XiWrappedContentSigner;
import org.xipki.security.util.GMUtil;
import org.xipki.security.util.SignerUtil;
import org.xipki.util.Args;
import org.xipki.util.CollectionUtil;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.EllipticCurve;
import java.util.ArrayList;
import java.util.List;

/**
 * Builder of signer based PKCS#12 keystore.
 *
 * @author Lijun Liao (xipki)
 * @since 2.0.0
 */

public class P12ContentSignerBuilder {

  private static class RSAContentSignerBuilder extends BcContentSignerBuilder {

    private final SignAlgo signAlgo;

    private RSAContentSignerBuilder(SignAlgo signAlgo) {
      super(signAlgo.getAlgorithmIdentifier(), signAlgo.getHashAlgo().getAlgorithmIdentifier());
      this.signAlgo = signAlgo;
    }

    protected Signer createSigner(AlgorithmIdentifier sigAlgId, AlgorithmIdentifier digAlgId)
        throws OperatorCreationException {
      signAlgo.assertSameAlgorithm(sigAlgId, digAlgId);
      if (signAlgo.isRSAPSSSigAlgo()) {
        try {
          return SignerUtil.createPSSRSASigner(signAlgo);
        } catch (XiSecurityException ex) {
          throw new OperatorCreationException(ex.getMessage(), ex);
        }
      } else {
        Digest dig = digestProvider.get(digAlgId);
        return new RSADigestSigner(dig);
      }
    }

  } // class RSAContentSignerBuilder

  private static class ECDSAContentSignerBuilder extends BcContentSignerBuilder {

    private final SignAlgo signAlgo;

    private ECDSAContentSignerBuilder(SignAlgo signAlgo) {
      super(signAlgo.getAlgorithmIdentifier(), signAlgo.getHashAlgo().getAlgorithmIdentifier());
      this.signAlgo = signAlgo;
    }

    protected Signer createSigner(AlgorithmIdentifier sigAlgId, AlgorithmIdentifier digAlgId)
        throws OperatorCreationException {
      signAlgo.assertSameAlgorithm(sigAlgId, digAlgId);

      Digest dig = signAlgo.getHashAlgo().createDigest();
      return new DSADigestSigner(new ECDSASigner(), dig);
    }

  } // class ECDSAContentSignerBuilder

  private static class SM2ContentSignerBuilder extends BcContentSignerBuilder {

    private final SignAlgo signAlgo;

    private SM2ContentSignerBuilder(SignAlgo signAlgo) {
      super(signAlgo.getAlgorithmIdentifier(), signAlgo.getHashAlgo().getAlgorithmIdentifier());
      this.signAlgo = signAlgo;
    }

    protected Signer createSigner(AlgorithmIdentifier sigAlgId, AlgorithmIdentifier digAlgId)
        throws OperatorCreationException {
      signAlgo.assertSameAlgorithm(sigAlgId, digAlgId);
      return new SM2Signer(signAlgo.getHashAlgo().createDigest());
    }

  } // class SM2ContentSignerBuilder

  private final PrivateKey key;

  private final PublicKey publicKey;

  private final X509Cert[] certificateChain;

  public P12ContentSignerBuilder(PrivateKey privateKey, PublicKey publicKey) {
    this.key = Args.notNull(privateKey, "privateKey");
    this.publicKey = Args.notNull(publicKey, "publicKey");
    this.certificateChain = null;
  }

  public P12ContentSignerBuilder(KeypairWithCert keypairWithCert) {
    this.key = Args.notNull(keypairWithCert, "keypairWithCert").getKey();
    this.publicKey = keypairWithCert.getPublicKey();
    this.certificateChain = keypairWithCert.getCertificateChain();
  }

  public X509Cert getCertificate() {
    return (certificateChain != null && certificateChain.length > 0) ? certificateChain[0] : null;
  }

  public X509Cert[] getCertificateChain() {
    return certificateChain;
  }

  public PrivateKey getKey() {
    return key;
  }

  public ContentSigner createContentSigner(SignAlgo signAlgo, SecureRandom random) throws XiSecurityException {
    String provName = getProviderName(Args.notNull(signAlgo, "signAlgo"));

    if (provName != null && Security.getProvider(provName) != null) {
      try {
        Signature signature = createSignature(signAlgo, provName, true);
        return new SignatureSigner(signAlgo, signature, key);
      } catch (Exception ex) {
        // do nothing
      }
    }

    Object[] rv = ff(signAlgo, random);
    BcContentSignerBuilder signerBuilder = (BcContentSignerBuilder) rv[0];
    AsymmetricKeyParameter keyparam = (AsymmetricKeyParameter) rv[1];

    try {
      return signerBuilder.build(keyparam);
    } catch (OperatorCreationException ex) {
      throw new XiSecurityException("operator creation error", ex);
    }
  } // method createContentSigner

  public ConcurrentContentSigner createSigner(SignAlgo signAlgo, int parallelism, SecureRandom random)
      throws XiSecurityException {
    List<XiContentSigner> signers = new ArrayList<>(Args.positive(parallelism, "parallelism"));
    String provName = getProviderName(Args.notNull(signAlgo, "signAlgo"));
    if (provName != null && Security.getProvider(provName) != null) {
      try {
        for (int i = 0; i < parallelism; i++) {
          Signature signature = createSignature(signAlgo, provName, i == 0);
          XiContentSigner signer = new SignatureSigner(signAlgo, signature, key);
          signers.add(signer);
        }
      } catch (Exception ex) {
        signers.clear();
      }
    }

    if (CollectionUtil.isEmpty(signers)) {
      Object[] rv = ff(signAlgo, random);
      BcContentSignerBuilder signerBuilder = (BcContentSignerBuilder) rv[0];
      AsymmetricKeyParameter keyparam = (AsymmetricKeyParameter) rv[1];

      for (int i = 0; i < parallelism; i++) {
        ContentSigner signer;
        try {
          signer = signerBuilder.build(keyparam);
        } catch (OperatorCreationException ex) {
          throw new XiSecurityException("operator creation error", ex);
        }
        signers.add(new XiWrappedContentSigner(signer, true));
      }
    }

    final boolean mac = false;
    ConcurrentContentSigner concurrentSigner;
    try {
      concurrentSigner = new DfltConcurrentContentSigner(mac, signers, key);
    } catch (NoSuchAlgorithmException ex) {
      throw new XiSecurityException(ex.getMessage(), ex);
    }

    if (certificateChain != null) {
      concurrentSigner.setCertificateChain(certificateChain);
    } else {
      concurrentSigner.setPublicKey(publicKey);
    }
    return concurrentSigner;
  } // method createSigner

  private String getProviderName(SignAlgo signAlgo) {
    if (signAlgo.isRSAPkcs1SigAlgo()) {
      return "SunRsaSign";
    } else if (signAlgo.isECDSASigAlgo()) {
      // Currently, the provider SunEC is much slower (5x) than BC,
      // so we do not use the Signature variant.
      return null;
    } else if (signAlgo.isEDDSASigAlgo()) {
      return "BC";
    } else {
      return null;
    }
  }

  private Signature createSignature(SignAlgo signAlgo, String provName, boolean test)
      throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {
    Signature signature = Signature.getInstance(signAlgo.getJceName(), provName);
    signature.initSign(key);
    if (test) {
      signature.update(new byte[]{1, 2, 3, 4});
      signature.sign();
    }
    return signature;
  }

  private Object[] ff(SignAlgo signAlgo, SecureRandom random) throws XiSecurityException {
    BcContentSignerBuilder signerBuilder;
    AsymmetricKeyParameter keyparam;
    try {
      if (key instanceof RSAPrivateKey) {
        if (!(signAlgo.isRSAPSSSigAlgo() || signAlgo.isRSAPkcs1SigAlgo())) {
          throw new NoSuchAlgorithmException(
              "the given algorithm is not a valid RSA signature algorithm '" + signAlgo + "'");
        }
        keyparam = SignerUtil.generateRSAPrivateKeyParameter((RSAPrivateKey) key);
        signerBuilder = new RSAContentSignerBuilder(signAlgo);
      } else if (key instanceof ECPrivateKey) {
        keyparam = ECUtil.generatePrivateKeyParameter(key);
        EllipticCurve curve = ((ECPrivateKey) key).getParams().getCurve();
        if (GMUtil.isSm2primev2Curve(curve)) {
          if (!signAlgo.isSM2SigAlgo()) {
            throw new NoSuchAlgorithmException(
                "the given algorithm is not a valid SM2 signature algorithm " + signAlgo);
          }
          signerBuilder = new SM2ContentSignerBuilder(signAlgo);
        } else {
          if (!signAlgo.isECDSASigAlgo()) {
            throw new NoSuchAlgorithmException(
                "the given algorithm is not a valid ECDSA signature algorithm " + signAlgo);
          }
          signerBuilder = new ECDSAContentSignerBuilder(signAlgo);
        }
      } else {
        throw new XiSecurityException("unsupported key " + key.getClass().getName());
      }
    } catch (InvalidKeyException ex) {
      throw new XiSecurityException("invalid key: " + ex.getMessage(), ex);
    } catch (NoSuchAlgorithmException ex) {
      throw new XiSecurityException("no such algorithm: " + ex.getMessage(), ex);
    }

    if (random != null) {
      signerBuilder.setSecureRandom(random);
    }

    return new Object[] {signerBuilder, keyparam};
  }

}
