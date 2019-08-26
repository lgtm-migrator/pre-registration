package io.mosip.kernel.crypto.jce.core;

import java.security.InvalidAlgorithmParameterException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PSource.PSpecified;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import io.mosip.kernel.core.crypto.exception.InvalidDataException;
import io.mosip.kernel.core.crypto.exception.InvalidKeyException;
import io.mosip.kernel.core.crypto.exception.InvalidParamSpecException;
import io.mosip.kernel.core.crypto.exception.SignatureException;
import io.mosip.kernel.core.crypto.spi.CryptoCoreSpec;
import io.mosip.kernel.core.exception.NoSuchAlgorithmException;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.crypto.jce.constant.SecurityExceptionCodeConstant;
import io.mosip.kernel.crypto.jce.util.CryptoUtils;

/**
 * This class provided <b> Basic and Core Cryptographic functionalities </b>.
 * 
 * This class follows {@link CryptoCoreSpec} and implement all basic
 * Cryptographic functions.
 * 
 * @author Urvil Joshi
 * @since 1.0.0
 * 
 * @see CryptoCoreSpec
 * @see PrivateKey
 * @see PublicKey
 * @see Signature
 * @see SecretKey
 * @see Cipher
 * @see GCMParameterSpec
 * @see SecureRandom
 */
//Code optimization remaining (Code Dupe)
@Component
public class CryptoCore
		implements CryptoCoreSpec<byte[], byte[], SecretKey, PublicKey, PrivateKey, String, SecureRandom, char[]> {

	// will be changed later will come from property files
	// @Value("${mosip.kernel.crypto.gcm-tag-length}")
	private static final String MGF1 = "MGF1";
	// will be changed later will come from property files
	// @Value("${mosip.kernel.crypto.gcm-tag-length}")
	private static final String SHA_256 = "SHA-256";
	// will be changed later will come from property files
	// @Value("${mosip.kernel.crypto.gcm-tag-length}")
	private static final String AES = "AES";

	// will be changed later will come from property files
	// @Value("${mosip.kernel.crypto.gcm-tag-length}")
	private int tagLength = 128;

	// will be changed later will come from property files
	// @Value("${mosip.kernel.crypto.symmetric-algorithm-name}")
	private String symmetricAlgorithm = "AES/GCM/PKCS5Padding";

	// will be changed later will come from property files
	// @Value("${mosip.kernel.crypto.asymmetric-algorithm-name}")
	private String asymmetricAlgorithm = "RSA/ECB/OAEPWITHSHA-256ANDMGF1PADDING";

	// will be changed later will come from property files
	// @Value("${mosip.kernel.crypto.hash-algorithm-name}")
	private String passwordAlgorithm = "PBKDF2WithHmacSHA512";

	// will be changed later will come from property files
	// @Value("${mosip.kernel.crypto.hash-algorithm-name}")
	private String signAlgorithm = "SHA512withRSA";

	// will be changed later will come from property files
	// @Value("${mosip.kernel.crypto.hash-algorithm-name}")
	private int symmetricKeyLength = 256;

	// will be changed later will come from property files
	// @Value("${mosip.kernel.crypto.hash-iteration}")
	private int iterations = 100000;

	private Map<String, Cipher> cipherRegistry;

	private SecureRandom secureRandom;

	private SecretKeyFactory secretKeyFactory;

	private Signature signature;

	@PostConstruct
	public void init() {
		cipherRegistry = new HashMap<>();
		try {
			cipherRegistry.put(symmetricAlgorithm, Cipher.getInstance(symmetricAlgorithm));
			cipherRegistry.put(asymmetricAlgorithm, Cipher.getInstance(asymmetricAlgorithm));
			secretKeyFactory = SecretKeyFactory.getInstance(passwordAlgorithm);
			signature = Signature.getInstance(signAlgorithm);
		} catch (java.security.NoSuchAlgorithmException | NoSuchPaddingException e) {
			throw new NoSuchAlgorithmException(
					SecurityExceptionCodeConstant.MOSIP_NO_SUCH_ALGORITHM_EXCEPTION.getErrorCode(),
					SecurityExceptionCodeConstant.MOSIP_NO_SUCH_ALGORITHM_EXCEPTION.getErrorMessage(), e);
		}
		secureRandom = new SecureRandom();
	}

	@Override
	public byte[] symmetricEncrypt(SecretKey key, byte[] data, byte[] aad) {
		Cipher cipher = cipherRegistry.get(symmetricAlgorithm);
		CryptoUtils.verifyData(data);
		byte[] output = null;
		byte[] randomIV = generateIV(cipher.getBlockSize());
		try {
			SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), AES);
			GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(tagLength, randomIV);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);
			output = new byte[cipher.getOutputSize(data.length) + cipher.getBlockSize()];
			if (aad != null && aad.length != 0) {
				cipher.updateAAD(aad);
			}
			byte[] processData = doFinal(data, cipher);
			System.arraycopy(processData, 0, output, 0, processData.length);
			System.arraycopy(randomIV, 0, output, processData.length, randomIV.length);
		} catch (java.security.InvalidKeyException e) {
			throw new InvalidKeyException(SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorCode(),
					SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorMessage(), e);
		} catch (InvalidAlgorithmParameterException e) {
			throw new InvalidKeyException(
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorCode(),
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorMessage(), e);
		}
		return output;
	}

	@Override
	public byte[] symmetricEncrypt(SecretKey key, byte[] data, byte[] iv, byte[] aad) {
		Cipher cipher = cipherRegistry.get(symmetricAlgorithm);
		CryptoUtils.verifyData(data);
		try {
			SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), AES);
			GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(tagLength, iv);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);
			if (aad != null && aad.length != 0) {
				cipher.updateAAD(aad);
			}
			return doFinal(data, cipher);
		} catch (java.security.InvalidKeyException e) {
			throw new InvalidKeyException(SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorCode(),
					SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorMessage(), e);
		} catch (InvalidAlgorithmParameterException e) {
			throw new InvalidParamSpecException(
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorCode(),
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorMessage(), e);
		}
	}

	@Override
	public byte[] symmetricDecrypt(SecretKey key, byte[] data, byte[] aad) {
		Cipher cipher = cipherRegistry.get(symmetricAlgorithm);
		CryptoUtils.verifyData(data);
		byte[] output = null;
		byte[] randomIV = Arrays.copyOfRange(data, data.length - cipher.getBlockSize(), data.length);
		try {
			SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), AES);
			GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(tagLength, randomIV);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
			if (aad != null && aad.length != 0) {
				cipher.updateAAD(aad);
			}
			output = doFinal(Arrays.copyOf(data, data.length - cipher.getBlockSize()), cipher);
		} catch (java.security.InvalidKeyException e) {
			throw new InvalidKeyException(SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorCode(),
					SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorMessage(), e);
		} catch (InvalidAlgorithmParameterException e) {
			throw new InvalidKeyException(
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorCode(),
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorMessage(), e);
		} catch (ArrayIndexOutOfBoundsException e) {
			throw new InvalidDataException(
					SecurityExceptionCodeConstant.MOSIP_INVALID_DATA_LENGTH_EXCEPTION.getErrorCode(),
					SecurityExceptionCodeConstant.MOSIP_INVALID_DATA_LENGTH_EXCEPTION.getErrorMessage(), e);
		}
		return output;
	}

	/**
	 * This method is overloaded from specification provided as it has GCM specific
	 * (AEAD) functionalities.
	 * 
	 * GCM is described in <a href=
	 * "https://en.wikipedia.org/wiki/Galois/Counter_Mode">Galois_Counter_Mode</a>.
	 * 
	 * AEAD is Authenticated Encryption with Associated Data which provides both
	 * confidentiality and data origin authentication.AEAD is described in <a href=
	 * "https://tools.ietf.org/html/rfc5116#section-3.3">rfc5116#section-3.3</a>.
	 * 
	 * AAD(Advance Authentication Data is additional data included with GCM ) The
	 * aim of AAD is to attach information to the ciphertext that is not encrypted,
	 * but is bound to the ciphertext in the sense that it cannot be changed or
	 * separated.Conceptually, the MAC is computed over the AAD and the ciphertext
	 * together.
	 * 
	 * @param key  symmetric key as key
	 * @param data data to encrypt
	 * @param iv   Initialization Vector
	 * @param aad  Advance Authentication Data is additional data included with GCM
	 *             to verify integrity of it
	 * @return
	 */
	@Override
	public byte[] symmetricDecrypt(SecretKey key, byte[] data, byte[] iv, byte[] aad) {
		Cipher cipher = cipherRegistry.get(symmetricAlgorithm);
		CryptoUtils.verifyData(data);
		try {
			SecretKeySpec keySpec = new SecretKeySpec(key.getEncoded(), AES);
			GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(tagLength, iv);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
			if (aad != null) {
				cipher.updateAAD(aad);
			}
			return doFinal(data, cipher);
		} catch (java.security.InvalidKeyException e) {
			throw new InvalidKeyException(SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorCode(),
					SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorMessage(), e);
		} catch (InvalidAlgorithmParameterException e) {
			throw new InvalidParamSpecException(
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorCode(),
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorMessage(), e);
		}
	}

	@Override
	public byte[] asymmetricEncrypt(PublicKey key, byte[] data) {
		CryptoUtils.verifyData(data);
		Cipher cipher = cipherRegistry.get(asymmetricAlgorithm);
		final OAEPParameterSpec oaepParams = new OAEPParameterSpec(SHA_256, MGF1, new MGF1ParameterSpec(SHA_256),
				PSpecified.DEFAULT);
		try {
			cipher.init(Cipher.ENCRYPT_MODE, key, oaepParams);
		} catch (java.security.InvalidKeyException e) {
			throw new InvalidKeyException(SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorCode(),
					e.getMessage(), e);
		}catch (InvalidAlgorithmParameterException e) {
			throw new InvalidParamSpecException(
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorCode(),
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorMessage(), e);
		}
		return doFinal(data, cipher);
	}

	@Override
	public byte[] asymmetricDecrypt(PrivateKey key, byte[] data) {
		CryptoUtils.verifyData(data);
		Cipher cipher = cipherRegistry.get(asymmetricAlgorithm);
		final OAEPParameterSpec oaepParams = new OAEPParameterSpec(SHA_256, MGF1, new MGF1ParameterSpec(SHA_256),
				PSpecified.DEFAULT);
		try {
			cipher.init(Cipher.DECRYPT_MODE, key, oaepParams);
		} catch (java.security.InvalidKeyException e) {
			throw new InvalidKeyException(SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorCode(),
					e.getMessage(), e);
		} catch (InvalidAlgorithmParameterException e) {
			throw new InvalidParamSpecException(
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorCode(),
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorMessage(), e);
		}
		return doFinal(data, cipher);
	}

	@Override
	public byte[] hash(char[] data, byte[] salt) {
		PBEKeySpec pbeKeySpec = new PBEKeySpec(data, salt, iterations, symmetricKeyLength);
		SecretKey key;
		try {
			key = secretKeyFactory.generateSecret(pbeKeySpec);
		} catch (InvalidKeySpecException e) {
			throw new InvalidParamSpecException(
					SecurityExceptionCodeConstant.MOSIP_INVALID_PARAM_SPEC_EXCEPTION.getErrorCode(), e.getMessage(), e);
		}
		return key.getEncoded();
	}

	@Override
	public String sign(byte[] data, PrivateKey privateKey) {
		try {
			signature.initSign(privateKey);
			signature.update(data);
			return CryptoUtil.encodeBase64String(signature.sign());
		} catch (java.security.InvalidKeyException e) {
			throw new InvalidKeyException(SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorCode(),
					e.getMessage(), e);
		} catch (java.security.SignatureException e) {
			throw new SignatureException(SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorCode(),
					e.getMessage(), e);
		}
	}

	@Override
	public boolean verifySignature(byte[] data, String sign, PublicKey publicKey) {
		try {
			signature.initVerify(publicKey);
			signature.update(data);
			return signature.verify(CryptoUtil.decodeBase64(sign));
		} catch (java.security.InvalidKeyException e) {
			throw new InvalidKeyException(SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorCode(),
					e.getMessage(), e);
		} catch (java.security.SignatureException e) {
			throw new SignatureException(SecurityExceptionCodeConstant.MOSIP_INVALID_KEY_EXCEPTION.getErrorCode(),
					e.getMessage(), e);
		}

	}

	@Override
	public SecureRandom random() {
		return secureRandom;
	}

	/**
	 * Generator for IV(Initialisation Vector)
	 * 
	 * @param blockSize blocksize of current cipher
	 * @return generated IV
	 */
	private byte[] generateIV(int blockSize) {
		byte[] byteIV = new byte[blockSize];
		secureRandom.nextBytes(byteIV);
		return byteIV;
	}

	private byte[] doFinal(byte[] data, Cipher cipher) {
		try {
			return cipher.doFinal(data);
		} catch (IllegalBlockSizeException e) {
			throw new InvalidDataException(
					SecurityExceptionCodeConstant.MOSIP_INVALID_DATA_SIZE_EXCEPTION.getErrorCode(), e.getMessage(), e);
		} catch (BadPaddingException e) {
			throw new InvalidDataException(
					SecurityExceptionCodeConstant.MOSIP_INVALID_ENCRYPTED_DATA_CORRUPT_EXCEPTION.getErrorCode(),
					e.getMessage(), e);
		}
	}
}