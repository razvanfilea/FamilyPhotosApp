package net.theluckycoder.familyphotos.network

import android.content.Context
import android.util.Log
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.net.InetAddress

object SslUtils {

    private val certificate = HeldCertificate.decode("""
        -----BEGIN CERTIFICATE-----
        MIIDizCCAnOgAwIBAgIIEZCh8fRNn5UwDQYJKoZIhvcNAQELBQAwdDELMAkGA1UE
        BhMCUk8xDjAMBgNVBAgTBVNpYml1MQ4wDAYDVQQHEwVTaWJpdTEWMBQGA1UEChMN
        VGhlTHVja3lDb2RlcjEWMBQGA1UECxMNVGhlTHVja3lDb2RlcjEVMBMGA1UEAxMM
        UmF6dmFuIEZpbGVhMB4XDTIxMDYwOTE3MjYxOFoXDTMxMDYwNzE3MjYxOFowdDEL
        MAkGA1UEBhMCUk8xDjAMBgNVBAgTBVNpYml1MQ4wDAYDVQQHEwVTaWJpdTEWMBQG
        A1UEChMNVGhlTHVja3lDb2RlcjEWMBQGA1UECxMNVGhlTHVja3lDb2RlcjEVMBMG
        A1UEAxMMUmF6dmFuIEZpbGVhMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKC
        AQEAk5No6CIqx6upRt2oYXTXrPIX6Wu4pNody+mkOOTv0Kw/ltHdMx4YYC5bD95E
        xfar5AmkmPnS6ox+RwQHc3X4jX7Y+fhoHlbF/ethELuTY5Kg83WJbyu91oUnx9oO
        uJD2YrCswOx8i0lruU4zzCPcmjtlTYdiumFUEYZIMy4LLslwx43XVelCKZQMqtf7
        qQtrkI4beLxvfvgRhxb/mSBcclv2Xzh3OStDHGrS9mczUc3Xwc4nQEfWdI3ZgGgn
        22I+WYho3d7O9AqCk51NY+4PLyU+fPmggTounuGgyimlCtRFgHAB8ZLxqB7xdBW1
        QohJhTiI0OOpdHRomqFIYyUU3QIDAQABoyEwHzAdBgNVHQ4EFgQURNg+B3jAStu1
        mR5sYaP5+wf7mUkwDQYJKoZIhvcNAQELBQADggEBAGOtfgKhBvZYQJLUdVycTgew
        XwNa0CgnXEG9DM3E3auNiPcs5MUM60/33O+3uJIToXYwkvN/rOrlQYhR19/QRUoV
        HEiFuar9OqkGQeQEvOo3qHIyTD+Qpeu+I7K7TFVJLk3/aWF83QfNyESUrgi7aw7I
        Vg7TpCU2b5srbBc6fcaon2fperrZZjLWIFVidGT+0g8463Qd7pRby9PSQ/2sovfs
        zY0Nbg+vNf+0SnrWNRlK+6w3+zKoCMzKml5V2ObmgSnadErgXc0BzHgTIZth8LsQ
        mbG0WCJ7qSZZTUSKJ8y22WVxSQfbK10htRcdqeiM9ONRkPWaIYm7ug/2AnrfPog=
        -----END CERTIFICATE-----
        -----BEGIN PRIVATE KEY-----
        MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQCTk2joIirHq6lG
        3ahhdNes8hfpa7ik2h3L6aQ45O/QrD+W0d0zHhhgLlsP3kTF9qvkCaSY+dLqjH5H
        BAdzdfiNftj5+GgeVsX962EQu5NjkqDzdYlvK73WhSfH2g64kPZisKzA7HyLSWu5
        TjPMI9yaO2VNh2K6YVQRhkgzLgsuyXDHjddV6UIplAyq1/upC2uQjht4vG9++BGH
        Fv+ZIFxyW/ZfOHc5K0McatL2ZzNRzdfBzidAR9Z0jdmAaCfbYj5ZiGjd3s70CoKT
        nU1j7g8vJT58+aCBOi6e4aDKKaUK1EWAcAHxkvGoHvF0FbVCiEmFOIjQ46l0dGia
        oUhjJRTdAgMBAAECggEBAJBrNsPNA/6I8EyIiMputM3r0Ri+MWEqjvRJvktrR4D3
        v/cRg54Nup8NXlImGzl8D3VHNov6F70vJjjLKZuFfGrEEwR1YzclhfbazQ+58SAF
        k3sNsCRaMxpnDqoWrEdUnbmw2i6mf68zd7dNX4VMJwvMSnPXpPduXf4UYj2rtofe
        XiMbyiDeRk+PHXhISwHbJZnEwQV4sfIotgHbaJ89mdDWuRD0ePXu5/XHEvyKn+0D
        bAi51qiqYFa4mTr6YH+uaJRCePQq/Bfr4FsVMMTxlDGg9GP1ki5NgHMFesaXtJph
        y9TgEctxAAUBsdS3/Sbwqce4U/wQwG9ZVRSKn2Lc4eUCgYEA1vZm9jeF1iFwr5vH
        ni6EAt2sZzhKw/j7rAHXVGhuGUmFA0b3LncNMMHQS3t86jkPxGAUo5sBXMIrh/dM
        UmiwSFC3GSj1JX4g0K7/gWL9nN7LxovgH0TLuapqY0cfQTyfrjJU9Gha47TonF83
        YIaDCmIvCEulrCCYazWg31p+07MCgYEAr7+zcy0NRFssXERVH3oQt686WpymkZpG
        yHIQQkDC9XpGElJ5W7d2550D8Z0h7yz/lNL4ZWQzTst/1BT3n88WsQmNRyuOAp7z
        Tzu5+Vr/+CB0NDypD7tjkZ3MAJaNsjQqLh3Fo2v7QL4tYDp1OafmUSY63flJUtrR
        0EYzI7rjbS8CgYBwK4eyt2pZQH3ixPE3M6KjNDVAb3lkJcp+CT/pxcyd2WVVPnTB
        hFIXvSsKGFgoiLoXiON8M+hUFOZOPtdOsV47fhLtSBgUBmrtOkQNqjAY6ZF/+4Fq
        D6YXLjvxri07B31zksQN/V5gMAipgG4PKmG6y10rgP3kgeXHG+bkZf6ETQKBgQCD
        zIPLEDIu3SD7YNNlc6kt118vrOLNtqD2mXPP+7k5VqVOAEO6oG+vanUWsPxN3bUd
        6skVYHuJz9rhNYgudKNTyRIGGYe6N3HILcZeVfw4HD6JHiS7A5C8F1Zx6nYA+lXX
        l8QrtkMKXpSw4aYfwuZaXQ0wgk6WsDtAsiSP2wvLSQKBgQCGJTiCTxnut3Or6PTu
        sTN0vxL2LqLvaABaFN1OfbwwxYwyi1SQrvpvnM2Pt4Aks2wje8HlvFy8r+iJwziu
        ll8JpL2QtCAnSSz/tbyV26WdneC/xNZ9Znpl/yHwRnQ3gOKrPdTCHUpdDSR6VSf7
        PCK/BYjfCyEDS5b+kOA04lG5fQ==
        -----END PRIVATE KEY-----
    """.trimIndent())

    fun getCertificates(): HandshakeCertificates {
       /* val localhostCertificate: HeldCertificate = HeldCertificate.Builder()
            .addSubjectAlternativeName(InetAddress.getByName("razvanrares.go.ro").canonicalHostName)
            .build()*/

        val certificates: HandshakeCertificates = HandshakeCertificates.Builder()
//            .addTrustedCertificate(localhostCertificate.certificate)
//            .addInsecureHost("razvanrares.go.ro")
            .addTrustedCertificate(certificate.certificate)
            .build()
        return certificates
    }
}
