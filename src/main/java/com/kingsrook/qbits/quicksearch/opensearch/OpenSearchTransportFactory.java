/*
 * Copyright 2024 Kingsrook, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.quicksearch.opensearch;


import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Collection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kingsrook.qqq.backend.core.actions.customizers.QCodeLoader;
import com.kingsrook.qqq.backend.core.exceptions.QException;
import com.kingsrook.qqq.backend.core.instances.QMetaDataVariableInterpreter;
import com.kingsrook.qqq.backend.core.logging.QLogger;
import com.kingsrook.qqq.backend.core.utils.StringUtils;
import com.kingsrook.qbits.quicksearch.QuickSearchAuthMode;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitConfig;
import com.kingsrook.qbits.quicksearch.QuickSearchQBitProducer;
import com.kingsrook.qbits.quicksearch.QuickSearchTlsConfig;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.util.Timeout;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import static com.kingsrook.qqq.backend.core.logging.LogUtils.logPair;


/*******************************************************************************
 ** Builds the OpenSearch transport for a config: NONE or BASIC auth over the
 ** Apache HttpClient 5 transport with TLS, timeouts, pool size, user agent and
 ** the optional OpenSearchTransportCustomizer; or AWS_SIGV4 over AwsSdk2Transport.
 **
 ** Secret-bearing values (username, password, store passwords, paths) are
 ** interpreted here with QMetaDataVariableInterpreter, so ${env.X} references
 ** resolve only inside this factory.
 *******************************************************************************/
public class OpenSearchTransportFactory
{
   private static final QLogger LOG = QLogger.getLogger(OpenSearchTransportFactory.class);

   private static final String USER_AGENT = "qbit-quick-search/" + QuickSearchQBitProducer.getVersion();



   /*******************************************************************************
    **
    *******************************************************************************/
   public static OpenSearchTransport build(QuickSearchQBitConfig config) throws QException
   {
      try
      {
         JsonpMapper mapper = buildMapper();

         return switch(config.getEffectiveAuthMode())
         {
            case AWS_SIGV4 -> buildAwsTransport(config, mapper);
            case NONE, BASIC -> buildApacheTransport(config, mapper);
         };
      }
      catch(QException e)
      {
         throw (e);
      }
      catch(Exception e)
      {
         throw (new QException("Failed to create OpenSearch transport: " + e.getMessage(), e));
      }
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   static JsonpMapper buildMapper()
   {
      ObjectMapper objectMapper = new ObjectMapper();
      objectMapper.registerModule(new JavaTimeModule());
      objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
      return (new JacksonJsonpMapper(objectMapper));
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static OpenSearchTransport buildApacheTransport(QuickSearchQBitConfig config, JsonpMapper mapper) throws Exception
   {
      QMetaDataVariableInterpreter interpreter = new QMetaDataVariableInterpreter();

      HttpHost httpHost = new HttpHost(config.getEffectiveScheme(), config.getEffectiveHost(), config.getEffectivePort());

      BasicCredentialsProvider credentialsProvider = null;
      if(config.getEffectiveAuthMode() == QuickSearchAuthMode.BASIC)
      {
         String username = requireResolved(interpreter, config.getOpensearchUsername(), "opensearchUsername");
         String password = requireResolved(interpreter, config.getOpensearchPassword(), "opensearchPassword");
         credentialsProvider = new BasicCredentialsProvider();
         credentialsProvider.setCredentials(new AuthScope(httpHost), new UsernamePasswordCredentials(username, password.toCharArray()));
      }

      OpenSearchTransportCustomizer customizer = loadCustomizer(config);
      SSLContext                    sslContext = buildSslContext(config.getTls(), interpreter);
      HostnameVerifier              verifier   = buildHostnameVerifier(config.getTls());

      BasicCredentialsProvider finalCredentialsProvider = credentialsProvider;
      int                      maxConnections           = config.getMaxConnections() == null ? ApacheHttpClient5TransportBuilder.DEFAULT_MAX_CONN_TOTAL : config.getMaxConnections();
      int                      connectTimeout           = config.getConnectTimeoutMillis() == null ? ApacheHttpClient5TransportBuilder.DEFAULT_CONNECT_TIMEOUT_MILLIS : config.getConnectTimeoutMillis();
      int                      responseTimeout          = config.getResponseTimeoutMillis() == null ? ApacheHttpClient5TransportBuilder.DEFAULT_RESPONSE_TIMEOUT_MILLIS : config.getResponseTimeoutMillis();

      ApacheHttpClient5TransportBuilder builder = ApacheHttpClient5TransportBuilder
         .builder(httpHost)
         .setMapper(mapper)
         .setDefaultHeaders(new BasicHeader[] { new BasicHeader("User-Agent", USER_AGENT) })
         .setRequestConfigCallback(requestConfig -> requestConfig
            .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
            .setResponseTimeout(Timeout.ofMilliseconds(responseTimeout)))
         .setHttpClientConfigCallback(httpClientBuilder ->
         {
            ///////////////////////////////////////////////////////////////////////
            // httpclient5 5.6+ decompresses gzip responses itself but leaves    //
            // the Content-Encoding header, so the OpenSearch transport would    //
            // gunzip the body a second time and fail.  Let the transport alone //
            // handle compression.                                               //
            ///////////////////////////////////////////////////////////////////////
            httpClientBuilder.disableContentCompression();

            if(finalCredentialsProvider != null)
            {
               httpClientBuilder.setDefaultCredentialsProvider(finalCredentialsProvider);
            }

            ClientTlsStrategyBuilder tlsStrategyBuilder = ClientTlsStrategyBuilder.create();
            if(sslContext != null)
            {
               tlsStrategyBuilder.setSslContext(sslContext);
            }
            if(verifier != null)
            {
               tlsStrategyBuilder.setHostnameVerifier(verifier);
            }

            httpClientBuilder.setConnectionManager(PoolingAsyncClientConnectionManagerBuilder.create()
               .setTlsStrategy(tlsStrategyBuilder.build())
               .setMaxConnTotal(maxConnections)
               .setMaxConnPerRoute(maxConnections)
               .build());

            return (customizer == null ? httpClientBuilder : customizer.customizeHttpClient(httpClientBuilder));
         });

      if(customizer != null)
      {
         builder = customizer.customizeTransport(builder);
      }

      return (builder.build());
   }



   /*******************************************************************************
    ** AWS Signature V4 over the AWS SDK's Apache HTTP client. Credentials come
    ** from the default chain, or from STS when awsAssumeRoleArn is set.
    *******************************************************************************/
   private static OpenSearchTransport buildAwsTransport(QuickSearchQBitConfig config, JsonpMapper mapper) throws Exception
   {
      QMetaDataVariableInterpreter interpreter = new QMetaDataVariableInterpreter();

      String region  = config.getEffectiveAwsRegion();
      String service = config.getEffectiveAwsServiceName();
      String host    = config.getEffectiveHost();
      if(config.getEffectivePort() != null && config.getEffectivePort() != 443)
      {
         host = host + ":" + config.getEffectivePort();
      }

      AwsCredentialsProvider credentials = DefaultCredentialsProvider.create();
      String                 roleArn     = interpreter.interpret(config.getAwsAssumeRoleArn());
      if(StringUtils.hasContent(roleArn))
      {
         StsClient stsClient = StsClient.builder().region(Region.of(region)).credentialsProvider(credentials).build();
         credentials = StsAssumeRoleCredentialsProvider.builder()
            .stsClient(stsClient)
            .refreshRequest(AssumeRoleRequest.builder().roleArn(roleArn).roleSessionName("qbit-quick-search").build())
            .build();
      }

      LOG.info("Building AWS SigV4 OpenSearch transport", logPair("host", host), logPair("region", region), logPair("service", service), logPair("assumeRole", StringUtils.hasContent(roleArn)));

      return (new AwsSdk2Transport(
         ApacheHttpClient.builder().build(),
         host,
         service,
         Region.of(region),
         AwsSdk2TransportOptions.builder().setCredentials(credentials).setMapper(mapper).build()));
   }



   /*******************************************************************************
    ** Build an SSLContext for the tls block, or null for JVM defaults.
    *******************************************************************************/
   static SSLContext buildSslContext(QuickSearchTlsConfig tls, QMetaDataVariableInterpreter interpreter) throws Exception
   {
      if(tls == null)
      {
         return (null);
      }

      SSLContextBuilder builder = SSLContextBuilder.create();
      boolean           custom  = false;

      if(Boolean.TRUE.equals(tls.getInsecureSkipVerify()))
      {
         LOG.warn("tls.insecureSkipVerify is enabled: OpenSearch server certificates are NOT verified");
         builder.loadTrustMaterial(null, (chain, authType) -> true);
         custom = true;
      }
      else if(StringUtils.hasContent(tls.getCaCertificatePath()))
      {
         builder.loadTrustMaterial(loadPemTrustStore(requireResolved(interpreter, tls.getCaCertificatePath(), "tls.caCertificatePath")), null);
         custom = true;
      }
      else if(StringUtils.hasContent(tls.getTrustStorePath()))
      {
         String   password   = interpreter.interpret(tls.getTrustStorePassword());
         KeyStore trustStore = KeyStore.getInstance(StringUtils.hasContent(tls.getTrustStoreType()) ? tls.getTrustStoreType() : KeyStore.getDefaultType());
         try(InputStream inputStream = new FileInputStream(requireResolved(interpreter, tls.getTrustStorePath(), "tls.trustStorePath")))
         {
            trustStore.load(inputStream, password == null ? null : password.toCharArray());
         }
         builder.loadTrustMaterial(trustStore, null);
         custom = true;
      }

      if(StringUtils.hasContent(tls.getKeyStorePath()))
      {
         String   password = interpreter.interpret(tls.getKeyStorePassword());
         char[]   chars    = password == null ? new char[0] : password.toCharArray();
         KeyStore keyStore = KeyStore.getInstance(StringUtils.hasContent(tls.getKeyStoreType()) ? tls.getKeyStoreType() : KeyStore.getDefaultType());
         try(InputStream inputStream = new FileInputStream(requireResolved(interpreter, tls.getKeyStorePath(), "tls.keyStorePath")))
         {
            keyStore.load(inputStream, chars);
         }
         builder.loadKeyMaterial(keyStore, chars);
         custom = true;
      }

      return (custom ? builder.build() : null);
   }



   /*******************************************************************************
    ** The no-op verifier when insecureSkipVerify (warned in buildSslContext) or
    ** hostnameVerification=false is set, else null for the default verifier.
    *******************************************************************************/
   static HostnameVerifier buildHostnameVerifier(QuickSearchTlsConfig tls)
   {
      if(tls == null)
      {
         return (null);
      }

      if(Boolean.TRUE.equals(tls.getInsecureSkipVerify()))
      {
         return (NoopHostnameVerifier.INSTANCE);
      }

      if(Boolean.FALSE.equals(tls.getHostnameVerification()))
      {
         LOG.warn("tls.hostnameVerification is disabled: OpenSearch server certificates are NOT checked against the host name");
         return (NoopHostnameVerifier.INSTANCE);
      }

      return (null);
   }



   /*******************************************************************************
    ** Interpret a value that must resolve to content; null, empty and blank all
    ** count as missing. The error names the field and, for a ${...} reference,
    ** the reference itself, never a resolved value.
    *******************************************************************************/
   static String requireResolved(QMetaDataVariableInterpreter interpreter, String value, String fieldName) throws QException
   {
      String resolved = interpreter.interpret(value);
      if(StringUtils.hasContent(resolved))
      {
         return (resolved);
      }

      String  trimmed     = value == null ? "" : value.trim();
      boolean isReference = trimmed.startsWith("${") && trimmed.endsWith("}");
      throw (new QException(fieldName + " is missing or empty" + (isReference ? " after resolving " + trimmed : "")));
   }



   /*******************************************************************************
    ** Load one or more PEM certificates into an in-memory trust store.
    *******************************************************************************/
   static KeyStore loadPemTrustStore(String path) throws Exception
   {
      KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
      trustStore.load(null, null);

      try(InputStream inputStream = new FileInputStream(path))
      {
         Collection<? extends Certificate> certificates = CertificateFactory.getInstance("X.509").generateCertificates(inputStream);
         int                               i            = 0;
         for(Certificate certificate : certificates)
         {
            trustStore.setCertificateEntry("quick-search-ca-" + (i++), certificate);
         }
         if(i == 0)
         {
            throw (new QException("No certificates found in tls.caCertificatePath"));
         }
      }

      return (trustStore);
   }



   /*******************************************************************************
    **
    *******************************************************************************/
   private static OpenSearchTransportCustomizer loadCustomizer(QuickSearchQBitConfig config) throws QException
   {
      if(config.getTransportCustomizer() == null)
      {
         return (null);
      }

      OpenSearchTransportCustomizer customizer = QCodeLoader.getAdHoc(OpenSearchTransportCustomizer.class, config.getTransportCustomizer());
      if(customizer == null)
      {
         throw (new QException("Could not load transportCustomizer [" + config.getTransportCustomizer().getName() + "]"));
      }
      return (customizer);
   }

}
