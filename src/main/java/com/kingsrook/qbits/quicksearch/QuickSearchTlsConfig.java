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

package com.kingsrook.qbits.quicksearch;


import java.io.Serializable;
import com.fasterxml.jackson.annotation.JsonIgnore;


/*******************************************************************************
 ** TLS trust and client-certificate settings for the OpenSearch connection.
 **
 ** By default the JVM trust store and hostname verification are used. Supply
 ** caCertificatePath (a PEM bundle) or trustStorePath to trust a private CA;
 ** supply keyStorePath for client-certificate (mTLS) authentication.
 ** insecureSkipVerify disables certificate and hostname checks and is refused
 ** for non-loopback hosts unless allowInsecureInProduction is also set.
 ** hostnameVerification=false keeps certificate checks but skips the host name
 ** check; it is allowed for any host and logs a warning.
 **
 ** Path and password values may be ${env.X} / ${prop.X} references; they are
 ** interpreted when the transport is built, never stored resolved. A path that
 ** resolves to an empty value is treated as missing.
 *******************************************************************************/
public class QuickSearchTlsConfig implements Serializable
{
   private String  caCertificatePath;
   private String  trustStorePath;
   private String  trustStorePassword;
   private String  trustStoreType;
   private String  keyStorePath;
   private String  keyStorePassword;
   private String  keyStoreType;
   private Boolean hostnameVerification      = true;
   private Boolean insecureSkipVerify        = false;
   private Boolean allowInsecureInProduction = false;



   /*******************************************************************************
    ** Whether any custom trust or key material is configured.
    *******************************************************************************/
   @JsonIgnore
   public boolean hasCustomTrustOrKeyMaterial()
   {
      return (caCertificatePath != null || trustStorePath != null || keyStorePath != null);
   }



   /*******************************************************************************
    ** Getter for caCertificatePath
    *******************************************************************************/
   public String getCaCertificatePath()
   {
      return (this.caCertificatePath);
   }



   /*******************************************************************************
    ** Setter for caCertificatePath
    *******************************************************************************/
   public void setCaCertificatePath(String caCertificatePath)
   {
      this.caCertificatePath = caCertificatePath;
   }



   /*******************************************************************************
    ** Fluent setter for caCertificatePath
    *******************************************************************************/
   public QuickSearchTlsConfig withCaCertificatePath(String caCertificatePath)
   {
      this.caCertificatePath = caCertificatePath;
      return (this);
   }



   /*******************************************************************************
    ** Getter for trustStorePath
    *******************************************************************************/
   public String getTrustStorePath()
   {
      return (this.trustStorePath);
   }



   /*******************************************************************************
    ** Setter for trustStorePath
    *******************************************************************************/
   public void setTrustStorePath(String trustStorePath)
   {
      this.trustStorePath = trustStorePath;
   }



   /*******************************************************************************
    ** Fluent setter for trustStorePath
    *******************************************************************************/
   public QuickSearchTlsConfig withTrustStorePath(String trustStorePath)
   {
      this.trustStorePath = trustStorePath;
      return (this);
   }



   /*******************************************************************************
    ** Getter for trustStorePassword. Hidden from JSON so the secret (or its
    ** reference) never leaves the JVM.
    *******************************************************************************/
   @JsonIgnore
   public String getTrustStorePassword()
   {
      return (this.trustStorePassword);
   }



   /*******************************************************************************
    ** Setter for trustStorePassword
    *******************************************************************************/
   public void setTrustStorePassword(String trustStorePassword)
   {
      this.trustStorePassword = trustStorePassword;
   }



   /*******************************************************************************
    ** Fluent setter for trustStorePassword
    *******************************************************************************/
   public QuickSearchTlsConfig withTrustStorePassword(String trustStorePassword)
   {
      this.trustStorePassword = trustStorePassword;
      return (this);
   }



   /*******************************************************************************
    ** Getter for trustStoreType (JKS or PKCS12; defaults to the JVM default)
    *******************************************************************************/
   public String getTrustStoreType()
   {
      return (this.trustStoreType);
   }



   /*******************************************************************************
    ** Setter for trustStoreType
    *******************************************************************************/
   public void setTrustStoreType(String trustStoreType)
   {
      this.trustStoreType = trustStoreType;
   }



   /*******************************************************************************
    ** Fluent setter for trustStoreType
    *******************************************************************************/
   public QuickSearchTlsConfig withTrustStoreType(String trustStoreType)
   {
      this.trustStoreType = trustStoreType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for keyStorePath
    *******************************************************************************/
   public String getKeyStorePath()
   {
      return (this.keyStorePath);
   }



   /*******************************************************************************
    ** Setter for keyStorePath
    *******************************************************************************/
   public void setKeyStorePath(String keyStorePath)
   {
      this.keyStorePath = keyStorePath;
   }



   /*******************************************************************************
    ** Fluent setter for keyStorePath
    *******************************************************************************/
   public QuickSearchTlsConfig withKeyStorePath(String keyStorePath)
   {
      this.keyStorePath = keyStorePath;
      return (this);
   }



   /*******************************************************************************
    ** Getter for keyStorePassword. Hidden from JSON.
    *******************************************************************************/
   @JsonIgnore
   public String getKeyStorePassword()
   {
      return (this.keyStorePassword);
   }



   /*******************************************************************************
    ** Setter for keyStorePassword
    *******************************************************************************/
   public void setKeyStorePassword(String keyStorePassword)
   {
      this.keyStorePassword = keyStorePassword;
   }



   /*******************************************************************************
    ** Fluent setter for keyStorePassword
    *******************************************************************************/
   public QuickSearchTlsConfig withKeyStorePassword(String keyStorePassword)
   {
      this.keyStorePassword = keyStorePassword;
      return (this);
   }



   /*******************************************************************************
    ** Getter for keyStoreType
    *******************************************************************************/
   public String getKeyStoreType()
   {
      return (this.keyStoreType);
   }



   /*******************************************************************************
    ** Setter for keyStoreType
    *******************************************************************************/
   public void setKeyStoreType(String keyStoreType)
   {
      this.keyStoreType = keyStoreType;
   }



   /*******************************************************************************
    ** Fluent setter for keyStoreType
    *******************************************************************************/
   public QuickSearchTlsConfig withKeyStoreType(String keyStoreType)
   {
      this.keyStoreType = keyStoreType;
      return (this);
   }



   /*******************************************************************************
    ** Getter for hostnameVerification
    *******************************************************************************/
   public Boolean getHostnameVerification()
   {
      return (this.hostnameVerification);
   }



   /*******************************************************************************
    ** Setter for hostnameVerification
    *******************************************************************************/
   public void setHostnameVerification(Boolean hostnameVerification)
   {
      this.hostnameVerification = hostnameVerification;
   }



   /*******************************************************************************
    ** Fluent setter for hostnameVerification
    *******************************************************************************/
   public QuickSearchTlsConfig withHostnameVerification(Boolean hostnameVerification)
   {
      this.hostnameVerification = hostnameVerification;
      return (this);
   }



   /*******************************************************************************
    ** Getter for insecureSkipVerify
    *******************************************************************************/
   public Boolean getInsecureSkipVerify()
   {
      return (this.insecureSkipVerify);
   }



   /*******************************************************************************
    ** Setter for insecureSkipVerify
    *******************************************************************************/
   public void setInsecureSkipVerify(Boolean insecureSkipVerify)
   {
      this.insecureSkipVerify = insecureSkipVerify;
   }



   /*******************************************************************************
    ** Fluent setter for insecureSkipVerify
    *******************************************************************************/
   public QuickSearchTlsConfig withInsecureSkipVerify(Boolean insecureSkipVerify)
   {
      this.insecureSkipVerify = insecureSkipVerify;
      return (this);
   }



   /*******************************************************************************
    ** Getter for allowInsecureInProduction
    *******************************************************************************/
   public Boolean getAllowInsecureInProduction()
   {
      return (this.allowInsecureInProduction);
   }



   /*******************************************************************************
    ** Setter for allowInsecureInProduction
    *******************************************************************************/
   public void setAllowInsecureInProduction(Boolean allowInsecureInProduction)
   {
      this.allowInsecureInProduction = allowInsecureInProduction;
   }



   /*******************************************************************************
    ** Fluent setter for allowInsecureInProduction
    *******************************************************************************/
   public QuickSearchTlsConfig withAllowInsecureInProduction(Boolean allowInsecureInProduction)
   {
      this.allowInsecureInProduction = allowInsecureInProduction;
      return (this);
   }

}
