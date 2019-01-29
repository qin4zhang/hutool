package cn.hutool.crypto;

import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.DESKeySpec;
import javax.crypto.spec.DESedeKeySpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.CharUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;

/**
 * 密钥工具类
 * 
 * @author looly, Gsealy
 * @since 4.4.1
 */
public class KeyUtil {

	/** Java密钥库(Java Key Store，JKS)KEY_STORE */
	public static final String KEY_STORE = "JKS";
	public static final String X509 = "X.509";

	/**
	 * 默认密钥字节数
	 * 
	 * <pre>
	 * RSA/DSA
	 * Default Keysize 1024
	 * Keysize must be a multiple of 64, ranging from 512 to 1024 (inclusive).
	 * </pre>
	 */
	public static final int DEFAULT_KEY_SIZE = 1024;

	/**
	 * SM2默认曲线
	 * 
	 * <pre>
	 * Default SM2 curve
	 * </pre>
	 */
	public static final String SM2_DEFAULT_CURVE = "sm2p256v1";

	/**
	 * 生成 {@link SecretKey}，仅用于对称加密和摘要算法密钥生成
	 * 
	 * @param algorithm 算法，支持PBE算法
	 * @return {@link SecretKey}
	 */
	public static SecretKey generateKey(String algorithm) {
		return generateKey(algorithm, -1);
	}

	/**
	 * 生成 {@link SecretKey}，仅用于对称加密和摘要算法密钥生成
	 * 
	 * @param algorithm 算法，支持PBE算法
	 * @param keySize 密钥长度
	 * @return {@link SecretKey}
	 * @since 3.1.2
	 */
	public static SecretKey generateKey(String algorithm, int keySize) {
		final int slashIndex = algorithm.indexOf(CharUtil.SLASH);
		if (slashIndex > 0) {
			algorithm = algorithm.substring(0, slashIndex);
		}
		KeyGenerator keyGenerator;
		try {
			keyGenerator = KeyGenerator.getInstance(algorithm);
		} catch (NoSuchAlgorithmException e) {
			throw new CryptoException(e);
		}

		if (keySize > 0) {
			keyGenerator.init(keySize);
		}
		return keyGenerator.generateKey();
	}

	/**
	 * 生成 {@link SecretKey}，仅用于对称加密和摘要算法密钥生成
	 * 
	 * @param algorithm 算法
	 * @param key 密钥，如果为{@code null} 自动生成随机密钥
	 * @return {@link SecretKey}
	 */
	public static SecretKey generateKey(String algorithm, byte[] key) {
		Assert.notBlank(algorithm, "Algorithm is blank!");
		SecretKey secretKey = null;
		if (algorithm.startsWith("PBE")) {
			// PBE密钥
			secretKey = generatePBEKey(algorithm, (null == key) ? null : StrUtil.str(key, CharsetUtil.CHARSET_UTF_8).toCharArray());
		} else if (algorithm.startsWith("DES")) {
			// DES密钥
			secretKey = generateDESKey(algorithm, key);
		} else {
			// 其它算法密钥
			secretKey = (null == key) ? generateKey(algorithm) : new SecretKeySpec(key, algorithm);
		}
		return secretKey;
	}

	/**
	 * 生成 {@link SecretKey}
	 * 
	 * @param algorithm DES算法，包括DES、DESede等
	 * @param key 密钥
	 * @return {@link SecretKey}
	 */
	public static SecretKey generateDESKey(String algorithm, byte[] key) {
		if (StrUtil.isBlank(algorithm) || false == algorithm.startsWith("DES")) {
			throw new CryptoException("Algorithm [{}] is not a DES algorithm!");
		}

		SecretKey secretKey = null;
		if (null == key) {
			secretKey = generateKey(algorithm);
		} else {
			KeySpec keySpec;
			try {
				if (algorithm.startsWith("DESede")) {
					// DESede兼容
					keySpec = new DESedeKeySpec(key);
				} else {
					keySpec = new DESKeySpec(key);
				}
			} catch (InvalidKeyException e) {
				throw new CryptoException(e);
			}
			secretKey = generateKey(algorithm, keySpec);
		}
		return secretKey;
	}

	/**
	 * 生成PBE {@link SecretKey}
	 * 
	 * @param algorithm PBE算法，包括：PBEWithMD5AndDES、PBEWithSHA1AndDESede、PBEWithSHA1AndRC2_40等
	 * @param key 密钥
	 * @return {@link SecretKey}
	 */
	public static SecretKey generatePBEKey(String algorithm, char[] key) {
		if (StrUtil.isBlank(algorithm) || false == algorithm.startsWith("PBE")) {
			throw new CryptoException("Algorithm [{}] is not a PBE algorithm!");
		}

		if (null == key) {
			key = RandomUtil.randomString(32).toCharArray();
		}
		PBEKeySpec keySpec = new PBEKeySpec(key);
		return generateKey(algorithm, keySpec);
	}

	/**
	 * 生成 {@link SecretKey}，仅用于对称加密和摘要算法
	 * 
	 * @param algorithm 算法
	 * @param keySpec {@link KeySpec}
	 * @return {@link SecretKey}
	 */
	public static SecretKey generateKey(String algorithm, KeySpec keySpec) {
		try {
			SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(algorithm);
			return keyFactory.generateSecret(keySpec);
		} catch (Exception e) {
			throw new CryptoException(e);
		}
	}

	/**
	 * 生成私钥，仅用于非对称加密<br>
	 * 算法见：https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyFactory
	 * 
	 * @param algorithm 算法
	 * @param key 密钥
	 * @return 私钥 {@link PrivateKey}
	 */
	public static PrivateKey generatePrivateKey(String algorithm, byte[] key) {
		if (null == key) {
			return null;
		}
		return generatePrivateKey(algorithm, new PKCS8EncodedKeySpec(key));
	}

	/**
	 * 生成私钥，仅用于非对称加密<br>
	 * 算法见：https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyFactory
	 * 
	 * @param algorithm 算法
	 * @param keySpec {@link KeySpec}
	 * @return 私钥 {@link PrivateKey}
	 * @since 3.1.1
	 */
	public static PrivateKey generatePrivateKey(String algorithm, KeySpec keySpec) {
		if (null == keySpec) {
			return null;
		}
		algorithm = getAlgorithmAfterWith(algorithm);
		try {
			return KeyFactory.getInstance(algorithm).generatePrivate(keySpec);
		} catch (Exception e) {
			throw new CryptoException(e);
		}
	}

	/**
	 * 生成私钥，仅用于非对称加密
	 * 
	 * @param keyStore {@link KeyStore}
	 * @param alias 别名
	 * @param password 密码
	 * @return 私钥 {@link PrivateKey}
	 */
	public static PrivateKey generatePrivateKey(KeyStore keyStore, String alias, char[] password) {
		try {
			return (PrivateKey) keyStore.getKey(alias, password);
		} catch (Exception e) {
			throw new CryptoException(e);
		}
	}

	/**
	 * 生成公钥，仅用于非对称加密<br>
	 * 算法见：https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyFactory
	 * 
	 * @param algorithm 算法
	 * @param key 密钥
	 * @return 公钥 {@link PublicKey}
	 */
	public static PublicKey generatePublicKey(String algorithm, byte[] key) {
		if (null == key) {
			return null;
		}
		return generatePublicKey(algorithm, new X509EncodedKeySpec(key));
	}

	/**
	 * 生成公钥，仅用于非对称加密<br>
	 * 算法见：https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyFactory
	 * 
	 * @param algorithm 算法
	 * @param keySpec {@link KeySpec}
	 * @return 公钥 {@link PublicKey}
	 * @since 3.1.1
	 */
	public static PublicKey generatePublicKey(String algorithm, KeySpec keySpec) {
		if (null == keySpec) {
			return null;
		}
		algorithm = getAlgorithmAfterWith(algorithm);
		try {
			return KeyFactory.getInstance(algorithm).generatePublic(keySpec);
		} catch (Exception e) {
			throw new CryptoException(e);
		}
	}

	/**
	 * 生成用于非对称加密的公钥和私钥，仅用于非对称加密<br>
	 * 密钥对生成算法见：https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyPairGenerator
	 * 
	 * @param algorithm 非对称加密算法
	 * @return {@link KeyPair}
	 */
	public static KeyPair generateKeyPair(String algorithm) {
		return generateKeyPair(algorithm, DEFAULT_KEY_SIZE);
	}

	/**
	 * 生成用于非对称加密的公钥和私钥<br>
	 * 密钥对生成算法见：https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyPairGenerator
	 * 
	 * @param algorithm 非对称加密算法
	 * @param keySize 密钥模（modulus ）长度
	 * @return {@link KeyPair}
	 */
	public static KeyPair generateKeyPair(String algorithm, int keySize) {
		return generateKeyPair(algorithm, keySize, null);
	}

	/**
	 * 生成用于非对称加密的公钥和私钥<br>
	 * 密钥对生成算法见：https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyPairGenerator
	 * 
	 * @param algorithm 非对称加密算法
	 * @param keySize 密钥模（modulus ）长度
	 * @param seed 种子
	 * @return {@link KeyPair}
	 */
	public static KeyPair generateKeyPair(String algorithm, int keySize, byte[] seed) {
		// SM2算法需要单独定义其曲线生成
		if ("SM2".equalsIgnoreCase(algorithm)) {
			final ECGenParameterSpec sm2p256v1 = new ECGenParameterSpec(SM2_DEFAULT_CURVE);
			return generateKeyPair(algorithm, keySize, seed, sm2p256v1);
		}

		return generateKeyPair(algorithm, keySize, seed, (AlgorithmParameterSpec[]) null);
	}

	/**
	 * 生成用于非对称加密的公钥和私钥<br>
	 * 密钥对生成算法见：https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyPairGenerator
	 * 
	 * @param algorithm 非对称加密算法
	 * @param params {@link AlgorithmParameterSpec}
	 * @return {@link KeyPair}
	 * @since 4.3.3
	 */
	public static KeyPair generateKeyPair(String algorithm, AlgorithmParameterSpec params) {
		return generateKeyPair(algorithm, null, params);
	}

	/**
	 * 生成用于非对称加密的公钥和私钥<br>
	 * 密钥对生成算法见：https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyPairGenerator
	 * 
	 * @param algorithm 非对称加密算法
	 * @param param {@link AlgorithmParameterSpec}
	 * @param seed 种子
	 * @return {@link KeyPair}
	 * @since 4.3.3
	 */
	public static KeyPair generateKeyPair(String algorithm, byte[] seed, AlgorithmParameterSpec param) {
		return generateKeyPair(algorithm, DEFAULT_KEY_SIZE, seed, param);
	}

	/**
	 * 生成用于非对称加密的公钥和私钥<br>
	 * 密钥对生成算法见：https://docs.oracle.com/javase/7/docs/technotes/guides/security/StandardNames.html#KeyPairGenerator
	 * 
	 * @param algorithm 非对称加密算法
	 * @param keySize 密钥模（modulus ）长度
	 * @param seed 种子
	 * @param params {@link AlgorithmParameterSpec}
	 * @return {@link KeyPair}
	 * @since 4.3.3
	 */
	public static KeyPair generateKeyPair(String algorithm, int keySize, byte[] seed, AlgorithmParameterSpec... params) {
		algorithm = getAlgorithmAfterWith(algorithm);
		final KeyPairGenerator keyPairGen = getKeyPairGenerator(algorithm);

		// 密钥模（modulus ）长度初始化定义
		if (keySize > 0) {
			// key长度适配修正
			if ("EC".equalsIgnoreCase(algorithm) && keySize > 256) {
				// 对于EC算法，密钥长度有限制，在此使用默认256
				keySize = 256;
			}
			if (null != seed) {
				keyPairGen.initialize(keySize, new SecureRandom(seed));
			} else {
				keyPairGen.initialize(keySize);
			}
		}

		// 自定义初始化参数
		if (ArrayUtil.isNotEmpty(params)) {
			for (AlgorithmParameterSpec param : params) {
				if (null == param) {
					continue;
				}
				try {
					if (null != seed) {
						keyPairGen.initialize(param, new SecureRandom(seed));
					} else {
						keyPairGen.initialize(param);
					}
				} catch (InvalidAlgorithmParameterException e) {
					throw new CryptoException(e);
				}
			}
		}
		return keyPairGen.generateKeyPair();
	}

	/**
	 * 获取{@link KeyPairGenerator}
	 * 
	 * @param algorithm 非对称加密算法
	 * @return {@link KeyPairGenerator}
	 * @since 4.3.3
	 */
	public static KeyPairGenerator getKeyPairGenerator(String algorithm) {
		Provider provider = null;
		try {
			provider = ProviderFactory.createBouncyCastleProvider();
		} catch (NoClassDefFoundError e) {
			// ignore
		}

		KeyPairGenerator keyPairGen;
		try {
			keyPairGen = (null == provider) ? KeyPairGenerator.getInstance(algorithm) : KeyPairGenerator.getInstance(algorithm, provider);
		} catch (NoSuchAlgorithmException e) {
			throw new CryptoException(e);
		}
		return keyPairGen;
	}

	/**
	 * 获取用于密钥生成的算法<br>
	 * 获取XXXwithXXX算法的后半部分算法，如果为ECDSA或SM2，返回算法为EC
	 * 
	 * @param algorithm XXXwithXXX算法
	 * @return 算法
	 */
	public static String getAlgorithmAfterWith(String algorithm) {
		Assert.notNull(algorithm, "algorithm must be not null !");
		int indexOfWith = StrUtil.lastIndexOfIgnoreCase(algorithm, "with");
		if (indexOfWith > 0) {
			algorithm = StrUtil.subSuf(algorithm, indexOfWith + "with".length());
		}
		if ("ECDSA".equalsIgnoreCase(algorithm) || "SM2".equalsIgnoreCase(algorithm)) {
			algorithm = "EC";
		}
		return algorithm;
	}

	/**
	 * 读取密钥库(Java Key Store，JKS) KeyStore文件<br>
	 * KeyStore文件用于数字证书的密钥对保存<br>
	 * see: http://snowolf.iteye.com/blog/391931
	 * 
	 * @param in {@link InputStream} 如果想从文件读取.keystore文件，使用 {@link FileUtil#getInputStream(java.io.File)} 读取
	 * @param password 密码
	 * @return {@link KeyStore}
	 */
	public static KeyStore readJKSKeyStore(InputStream in, char[] password) {
		return readKeyStore(KEY_STORE, in, password);
	}

	/**
	 * 读取KeyStore文件<br>
	 * KeyStore文件用于数字证书的密钥对保存<br>
	 * see: http://snowolf.iteye.com/blog/391931
	 * 
	 * @param type 类型
	 * @param in {@link InputStream} 如果想从文件读取.keystore文件，使用 {@link FileUtil#getInputStream(java.io.File)} 读取
	 * @param password 密码
	 * @return {@link KeyStore}
	 */
	public static KeyStore readKeyStore(String type, InputStream in, char[] password) {
		KeyStore keyStore = null;
		try {
			keyStore = KeyStore.getInstance(type);
			keyStore.load(in, password);
		} catch (Exception e) {
			throw new CryptoException(e);
		}
		return keyStore;
	}

	/**
	 * 从KeyStore中获取私钥公钥
	 * 
	 * @param type 类型
	 * @param in {@link InputStream} 如果想从文件读取.keystore文件，使用 {@link FileUtil#getInputStream(java.io.File)} 读取
	 * @param password 密码
	 * @param alias 别名
	 * @return {@link KeyPair}
	 * @since 4.4.1
	 */
	public static KeyPair getKeyPair(String type, InputStream in, char[] password, String alias) {
		final KeyStore keyStore = readKeyStore(type, in, password);
		return getKeyPair(keyStore, password, alias);
	}

	/**
	 * 从KeyStore中获取私钥公钥
	 * 
	 * @param keyStore {@link KeyStore}
	 * @param password 密码
	 * @param alias 别名
	 * @return {@link KeyPair}
	 * @since 4.4.1
	 */
	public static KeyPair getKeyPair(KeyStore keyStore, char[] password, String alias) {
		PublicKey publicKey;
		PrivateKey privateKey;
		try {
			publicKey = keyStore.getCertificate(alias).getPublicKey();
			privateKey = (PrivateKey) keyStore.getKey(alias, password);
		} catch (Exception e) {
			throw new CryptoException(e);
		}
		return new KeyPair(publicKey, privateKey);
	}

	/**
	 * 读取X.509 Certification文件<br>
	 * Certification为证书文件<br>
	 * see: http://snowolf.iteye.com/blog/391931
	 * 
	 * @param in {@link InputStream} 如果想从文件读取.cer文件，使用 {@link FileUtil#getInputStream(java.io.File)} 读取
	 * @param password 密码
	 * @param alias 别名
	 * @return {@link KeyStore}
	 * @since 4.4.1
	 */
	public static Certificate readX509Certificate(InputStream in, char[] password, String alias) {
		return readCertificate(X509, in, password, alias);
	}

	/**
	 * 读取X.509 Certification文件<br>
	 * Certification为证书文件<br>
	 * see: http://snowolf.iteye.com/blog/391931
	 * 
	 * @param in {@link InputStream} 如果想从文件读取.cer文件，使用 {@link FileUtil#getInputStream(java.io.File)} 读取
	 * @return {@link KeyStore}
	 * @since 4.4.1
	 */
	public static Certificate readX509Certificate(InputStream in) {
		return readCertificate(X509, in);
	}

	/**
	 * 读取Certification文件<br>
	 * Certification为证书文件<br>
	 * see: http://snowolf.iteye.com/blog/391931
	 * 
	 * @param type 类型，例如X.509
	 * @param in {@link InputStream} 如果想从文件读取.cer文件，使用 {@link FileUtil#getInputStream(java.io.File)} 读取
	 * @param password 密码
	 * @param alias 别名
	 * @return {@link KeyStore}
	 * @since 4.4.1
	 */
	public static Certificate readCertificate(String type, InputStream in, char[] password, String alias) {
		final KeyStore keyStore = readKeyStore(type, in, password);
		try {
			return keyStore.getCertificate(alias);
		} catch (KeyStoreException e) {
			throw new CryptoException(e);
		}
	}

	/**
	 * 读取Certification文件<br>
	 * Certification为证书文件<br>
	 * see: http://snowolf.iteye.com/blog/391931
	 * 
	 * @param type 类型，例如X.509
	 * @param in {@link InputStream} 如果想从文件读取.cer文件，使用 {@link FileUtil#getInputStream(java.io.File)} 读取
	 * @return {@link Certificate}
	 */
	public static Certificate readCertificate(String type, InputStream in) {
		Certificate certificate;
		try {
			certificate = CertificateFactory.getInstance(type).generateCertificate(in);
		} catch (Exception e) {
			throw new CryptoException(e);
		}
		return certificate;
	}

	/**
	 * 获得 Certification
	 * 
	 * @param keyStore {@link KeyStore}
	 * @param alias 别名
	 * @return {@link Certificate}
	 */
	public static Certificate getCertificate(KeyStore keyStore, String alias) {
		try {
			return keyStore.getCertificate(alias);
		} catch (Exception e) {
			throw new CryptoException(e);
		}
	}
	
	/**
	 * 编码压缩EC公钥（基于BouncyCastle）
	 * 
	 * @param publicKey {@link PublicKey}
	 * @return 压缩得到的X
	 */
	public static byte[] encodeECPublicKey(PublicKey publicKey) {
		org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey bcPubKey = (org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey) publicKey;
		return bcPubKey.getQ().getEncoded(true);
	}
	
	/**
	 * 解码恢复EC压缩公钥,支持Base64和Hex编码,（基于BouncyCastle）
	 * 
	 * @param encode 压缩公钥
	 * @param curveName EC曲线名
	 */
	public static PublicKey decodeECPoint(String encode, String curveName) {
		Provider provider = null;
		try {
			provider = ProviderFactory.createBouncyCastleProvider();
		} catch (NoClassDefFoundError e) {
			// ignore
		}
		
		final byte[] encodeByte = SecureUtil.decodeKey(encode);
		org.bouncycastle.jce.spec.ECNamedCurveParameterSpec namedSpec = org.bouncycastle.jce.ECNamedCurveTable.getParameterSpec(curveName);
		EllipticCurve ecCurve = new EllipticCurve(new ECFieldFp(namedSpec.getCurve().getField().getCharacteristic()),namedSpec.getCurve().getA().toBigInteger(), namedSpec.getCurve().getB().toBigInteger());
		// 根据X恢复点Y
		ECPoint point = org.bouncycastle.jce.ECPointUtil.decodePoint(ecCurve, encodeByte);
		
		// 根据曲线恢复公钥格式
		java.security.spec.ECParameterSpec ecSpec = new org.bouncycastle.jce.spec.ECNamedCurveSpec(curveName,
				namedSpec.getCurve(), namedSpec.getG(), namedSpec.getN());
		ECPublicKeySpec pubKeySpec = new ECPublicKeySpec(point, ecSpec);
		try {
			KeyFactory PubKeyGen = KeyFactory.getInstance("EC", provider);
			return PubKeyGen.generatePublic(pubKeySpec);
		} catch (GeneralSecurityException e) {
			throw new CryptoException(e);
		}
	}
}
