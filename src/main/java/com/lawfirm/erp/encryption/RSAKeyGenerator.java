package com.lawfirm.erp.encryption;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;


public class RSAKeyGenerator {


    public static void main(String[] args) throws Exception {
        generateAndPrintKeys();
    }


    public static void generateAndPrintKeys() throws Exception {
        // Generate RSA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();


        // Convert to Base64 strings
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());


        // Print keys in PEM format
        System.out.println("=== RSA PUBLIC KEY ===");
        System.out.println("-----BEGIN PUBLIC KEY-----");
        System.out.println(publicKeyBase64);
        System.out.println("-----END PUBLIC KEY-----");


        System.out.println("\n=== RSA PRIVATE KEY ===");
        System.out.println("-----BEGIN PRIVATE KEY-----");
        System.out.println(privateKeyBase64);
        System.out.println("-----END PRIVATE KEY-----");


        System.out.println("\n=== For application.properties ===");
        System.out.println("payment.encryption.rsa-public-key=" + publicKeyBase64);
        System.out.println("payment.encryption.rsa-private-key=" + privateKeyBase64);
    }
}
