package com.example.kafka.config;

import com.example.kafka.model.Order;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.security.protocol:PLAINTEXT}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.sasl.mechanism:}")
    private String saslMechanism;

    @Value("${spring.kafka.properties.sasl.jaas.config:}")
    private String saslJaasConfig;

    @Value("${spring.kafka.properties.ssl.endpoint.identification.algorithm:}")
    private String sslEndpointIdentificationAlgorithm;

    @Value("${spring.kafka.properties.ssl.truststore.location:}")
    private String sslTruststoreLocation;

    @Value("${spring.kafka.properties.ssl.truststore.password:}")
    private String sslTruststorePassword;

    /**
     * Resolve truststore location - handles both file paths and classpath resources
     * @param truststoreLocation The truststore location (can be file path or classpath:resource)
     * @return Absolute file path to the truststore
     */
    private String resolveTruststoreLocation(String truststoreLocation) {
        if (truststoreLocation == null || truststoreLocation.isEmpty()) {
            return null;
        }
        
        // If it's a classpath resource, extract it to a temp file
        if (truststoreLocation.startsWith("classpath:")) {
            String resourcePath = truststoreLocation.substring("classpath:".length());
            try {
                ClassPathResource resource = new ClassPathResource(resourcePath);
                if (resource.exists()) {
                    // Create a temporary file and copy the resource
                    File tempFile = File.createTempFile("kafka-truststore", ".jks");
                    tempFile.deleteOnExit();
                    
                    try (InputStream is = resource.getInputStream();
                         FileOutputStream fos = new FileOutputStream(tempFile)) {
                        is.transferTo(fos);
                    }
                    
                    return tempFile.getAbsolutePath();
                } else {
                    System.err.println("Warning: Truststore resource not found: " + resourcePath);
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not load truststore from classpath: " + e.getMessage());
            }
            return null;
        }
        
        // Otherwise, return as-is (assume it's a file path)
        return truststoreLocation;
    }

    /**
     * Load PEM certificate from resources and create a JKS truststore file
     * This allows using PEM certificates directly without manual conversion
     * @return Path to the created JKS truststore file, or null if certificate not found
     */
    private String createJKSFromPEM() {
        try {
            // Try to load PEM certificate from resources
            ClassPathResource certResource = new ClassPathResource("kafka-ca.crt");
            if (certResource.exists()) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                try (InputStream certInputStream = certResource.getInputStream()) {
                    Certificate cert = cf.generateCertificate(certInputStream);
                    
                    // Create a temporary JKS truststore file
                    File tempTruststore = File.createTempFile("kafka-truststore", ".jks");
                    tempTruststore.deleteOnExit();
                    
                    // Create KeyStore and add certificate
                    KeyStore trustStore = KeyStore.getInstance("JKS");
                    trustStore.load(null, null);
                    trustStore.setCertificateEntry("kafka-ca", cert);
                    
                    // Save to file (using empty password for development)
                    String password = "changeit";
                    try (FileOutputStream fos = new FileOutputStream(tempTruststore)) {
                        trustStore.store(fos, password.toCharArray());
                    }
                    
                    return tempTruststore.getAbsolutePath();
                }
            }
        } catch (Exception e) {
            // If PEM certificate loading fails, return null to use default truststore
            System.err.println("Warning: Could not load PEM certificate from resources, using default truststore: " + e.getMessage());
        }
        return null;
    }

    // Producer Configuration
    @Bean
    public ProducerFactory<String, Order> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Security configuration if provided
        if (securityProtocol != null && !securityProtocol.isEmpty() && !securityProtocol.equals("PLAINTEXT")) {
            configProps.put("security.protocol", securityProtocol);
        }
        if (saslMechanism != null && !saslMechanism.isEmpty()) {
            configProps.put("sasl.mechanism", saslMechanism);
        }
        if (saslJaasConfig != null && !saslJaasConfig.isEmpty()) {
            configProps.put("sasl.jaas.config", saslJaasConfig);
        }
        
        // SSL configuration
        if (securityProtocol != null && securityProtocol.contains("SSL")) {
            // SSL truststore configuration
            String truststorePath = resolveTruststoreLocation(sslTruststoreLocation);
            
            // If no truststore location is specified, try to create one from PEM certificate
            if (truststorePath == null && sslTruststorePassword == null) {
                truststorePath = createJKSFromPEM();
                if (truststorePath != null) {
                    // Use default password for auto-generated truststore
                    configProps.put("ssl.truststore.password", "changeit");
                }
            }
            
            if (truststorePath != null && !truststorePath.isEmpty()) {
                configProps.put("ssl.truststore.location", truststorePath);
                if (sslTruststorePassword != null && !sslTruststorePassword.isEmpty()) {
                    configProps.put("ssl.truststore.password", sslTruststorePassword);
                }
            }
            
            // Endpoint identification algorithm
            if (sslEndpointIdentificationAlgorithm != null) {
                configProps.put("ssl.endpoint.identification.algorithm", sslEndpointIdentificationAlgorithm);
            } else {
                // Default: disable hostname verification for development
                configProps.put("ssl.endpoint.identification.algorithm", "");
            }
        }
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Order> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // Consumer Configuration
    @Bean
    public ConsumerFactory<String, Order> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Security configuration if provided
        if (securityProtocol != null && !securityProtocol.isEmpty() && !securityProtocol.equals("PLAINTEXT")) {
            configProps.put("security.protocol", securityProtocol);
        }
        if (saslMechanism != null && !saslMechanism.isEmpty()) {
            configProps.put("sasl.mechanism", saslMechanism);
        }
        if (saslJaasConfig != null && !saslJaasConfig.isEmpty()) {
            configProps.put("sasl.jaas.config", saslJaasConfig);
        }
        
        // SSL configuration
        if (securityProtocol != null && securityProtocol.contains("SSL")) {
            // SSL truststore configuration
            String truststorePath = resolveTruststoreLocation(sslTruststoreLocation);
            
            // If no truststore location is specified, try to create one from PEM certificate
            if (truststorePath == null && sslTruststorePassword == null) {
                truststorePath = createJKSFromPEM();
                if (truststorePath != null) {
                    // Use default password for auto-generated truststore
                    configProps.put("ssl.truststore.password", "changeit");
                }
            }
            
            if (truststorePath != null && !truststorePath.isEmpty()) {
                configProps.put("ssl.truststore.location", truststorePath);
                if (sslTruststorePassword != null && !sslTruststorePassword.isEmpty()) {
                    configProps.put("ssl.truststore.password", sslTruststorePassword);
                }
            }
            
            // Endpoint identification algorithm
            if (sslEndpointIdentificationAlgorithm != null) {
                configProps.put("ssl.endpoint.identification.algorithm", sslEndpointIdentificationAlgorithm);
            } else {
                // Default: disable hostname verification for development
                configProps.put("ssl.endpoint.identification.algorithm", "");
            }
        }
        
        return new DefaultKafkaConsumerFactory<>(configProps, new StringDeserializer(),
                new JsonDeserializer<>(Order.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Order> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Order> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}

