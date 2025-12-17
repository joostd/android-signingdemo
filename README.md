# Signing Demo

A simple Android app that demonstrates the use of the Android Keystore for protecting cryptographic keys.
Validation of key attestations is typically done using a backend server. 
For this demo however, we simply log everything and validate by hand (see below).

# Install from source

- clone this repository

```bash
$ git clone git@github.com:joostd/android-signingdemo.git
...
$ cd android-signingdemo/
```

- connect an Android device (in debug mode)

- build and install

```bash
$ ./gradlew installDebug
...
```

## Troubleshooting

Make sure your SDK location can be found.
Either
- define a valid SDK location with an `ANDROID_HOME` environment variable
- set the `sdk.dir` path in your project's `local.properties` file

# Install from release

Alternatively, download the latest .apk file from
[https://github.com/joostd/android-signingdemo/releases](https://github.com/joostd/android-signingdemo/releases)

and use adb to install

```bash
$ wget https://github.com/joostd/android-signingdemo/releases/download/v0.0.1-alpha/app-debug.apk
...
$ adb install app-debug.apk
...
```

and start SigningDemo on your Android device.

# Key generation

The public part of generated keys are written to the Debug log whenever the "Generate EC P256 Key pair" button is tapped, together with some of its attributes.
For instance, on a Pixel device:

```
$ adb logcat -v raw -s keygen
keystore size: 0
Public Key: 3059301306072a8648ce3d020106082a8648ce3d03010703420004e7ff40b4e42357d889c5b172f06b1826daee73816a408e70796eb539ea1793f75a25a59a03e162fe1d9ceb7b4bfdaa61914f2658a3e7c3d3948e67f54ade9056
origin generated: yes
securityLevel: 2
strongbox: yes
```

Use OpenSSL to extract the details of the public key:

```
$ echo 3059301306072a8648ce3d020106082a8648ce3d03010703420004e7ff40b4e42357d889c5b172f06b1826daee73\
>      816a408e70796eb539ea1793f75a25a59a03e162fe1d9ceb7b4bfdaa61914f2658a3e7c3d3948e67f54ade9056 \
> | xxd -r -p | openssl pkey -pubin -noout -text
Public-Key: (256 bit)
pub:
    04:e7:ff:40:b4:e4:23:57:d8:89:c5:b1:72:f0:6b:
    18:26:da:ee:73:81:6a:40:8e:70:79:6e:b5:39:ea:
    17:93:f7:5a:25:a5:9a:03:e1:62:fe:1d:9c:eb:7b:
    4b:fd:aa:61:91:4f:26:58:a3:e7:c3:d3:94:8e:67:
    f5:4a:de:90:56
ASN1 OID: prime256v1
NIST CURVE: P-256
```

# Retrieve attestations

Attestations are written to the Debug log whenever the "Attest Key" button is tapped.

To retrieve those certificates, use adb:

```
$ adb logcat -v raw -s attest
...
```

This will show a number of hex-encoded certificates

```
#certs: 4
308202a63082...
308202003082...
...
```

The first certificate is the attestation itself, followed by a CA chain.

To inspect the attestation, first save the chain to a file (eg `attest.log`), and re-encode the certificates into PEM format:

```
$ grep -v '#' attest.log | while read line; do echo $line | xxd -r -p | openssl x509; done > certs.pem
```

The first certificate is the attestation, followed by the chain (in leaf to root order, i.e. the last certificate is the root).
Extract the attestation certificate using OpenSSL:

```
$ openssl x509 -in certs.pem -out attestation.pem
```

(Note that `openssl x509` only considers the first certificate in the file, which is the attestation certificate)

The root certificate must be one of the well-known roots, and they can be retrieved as follows:

```
$ curl https://android.googleapis.com/attestation/root | jq .[] -r > roots.pem
```

To validate the chain, use `openssl verify`:

```
$ openssl verify -show_chain -CAfile roots.pem -untrusted certs.pem attestation.pem
attestation.pem: OK
Chain:
depth=0: CN=Android Keystore Key (untrusted)
depth=1: title=StrongBox, serialNumber=06842f84bcbadbd196405bfd6a6349eb (untrusted)
depth=2: title=StrongBox, serialNumber=f3df197b141c9347c7daf0375ec0f949 (untrusted)
depth=3: serialNumber=f92009e853b6b045
```

(This example was generated on a Pixel 6a device.)

See also [here](https://developer.android.com/privacy-and-security/security-key-attestation#root_certificate)

Note that chain validation will only work on physical Android devices. 
Android emulators will use software keys that will not chain up to the root certificates in roots.pem.

# Revocation

Some Android OEM keys have been leaked.
For that reason, many of the issuing certificates for attestations have been revoked.

For instance, the key in the file [revoked-key.pem](revoked-key.pem) has been leaked.
Its public key is

```bash
$ openssl pkey -in revoked-key.pem -pubout
-----BEGIN PUBLIC KEY-----
MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAtctPD9EZ2n16S/0oZdfj
MpcLNJmRK+okaqixYYZ/dFuy1lQLSAYaj2BtNw/FivMJg6EEw8uffAcgVdFxs8mc
mIju7roxLhpLPfaNg8AMp2JyDz8LaNvfkXPYhARXKFTtsFvtP37AJZCALD1JMmFK
Uz7qdzJzrUmbth2f5t/pOCKcX2HaPhVu9zvDOQBE8/H8M75gN+heyWPqBfHkQjyw
ZRbJBLqcjn0wFDHgCWSk6cllUyaJoZPAG9tUxr1V1d9Sm/F/6IeDhKdIx8fJQQ/b
mc7wN1fDNMDxntg87oIvnpNhak49QsHK0WPTKpytaTWdE59bqC/mQs0wNYmzE9MB
TXsHKZZ99GJnPRVY8Ts155SbCQHGFVValdZ00Dh5BHR91AAbnxCwBDhwiii2dG6m
/SpQFQCz3BBQZk/fT6XBDNre7ZE4ONDp/RxnmXA3xVLtbuWwUFua5pasj4kTZAdZ
nq0hxDLMIgz/Vt58qNgLcq3ozxhDKzRUKYU6+dNe6SmJAgMBAAE=
-----END PUBLIC KEY-----
```

The Corresponding certificate is in the file [revoked-crt.pem](revoked-crt.pem).
This can be verified by inspecting the public key in the certificate:

```bash
$ openssl x509 -in revoked-crt.pem -noout -pubkey
-----BEGIN PUBLIC KEY-----
MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAtctPD9EZ2n16S/0oZdfj
MpcLNJmRK+okaqixYYZ/dFuy1lQLSAYaj2BtNw/FivMJg6EEw8uffAcgVdFxs8mc
mIju7roxLhpLPfaNg8AMp2JyDz8LaNvfkXPYhARXKFTtsFvtP37AJZCALD1JMmFK
Uz7qdzJzrUmbth2f5t/pOCKcX2HaPhVu9zvDOQBE8/H8M75gN+heyWPqBfHkQjyw
ZRbJBLqcjn0wFDHgCWSk6cllUyaJoZPAG9tUxr1V1d9Sm/F/6IeDhKdIx8fJQQ/b
mc7wN1fDNMDxntg87oIvnpNhak49QsHK0WPTKpytaTWdE59bqC/mQs0wNYmzE9MB
TXsHKZZ99GJnPRVY8Ts155SbCQHGFVValdZ00Dh5BHR91AAbnxCwBDhwiii2dG6m
/SpQFQCz3BBQZk/fT6XBDNre7ZE4ONDp/RxnmXA3xVLtbuWwUFua5pasj4kTZAdZ
nq0hxDLMIgz/Vt58qNgLcq3ozxhDKzRUKYU6+dNe6SmJAgMBAAE=
-----END PUBLIC KEY-----
```

To check the revocation status of this certificate, instead of using a CRL, as JSON file is published at URL
[https://android.googleapis.com/attestation/status](https://android.googleapis.com/attestation/status)

This file contains the serial numbers of all revoked certificates.
So to check revocation, we need to extract the serial number of the certificate we want to check for revocation:

```bash
$ openssl x509 -in revoked-crt.pem -noout -serial | tr A-Z a-z
serial=fe29ff268201c69ad7f515ae9085f902
```

Note that we use tr to convert the serial number to lowercase, as that is what is used in the revocation list.

We can see the certificate is revoked by using the lowercase serial number as a key:

```bash
$ curl -s https://android.googleapis.com/attestation/status | jq .entries.fe29ff268201c69ad7f515ae9085f902
{
  "status": "REVOKED",
  "reason": "KEY_COMPROMISE"
}
```

Note that when retrieving the full chain from the logs, the attestation certificate is listed first,
followed by one or more intermediate certificates, and finally the root certificate.
As the attestation certificate is specific to the generated KeyStore key, the intermediate certificate(s) are the ones than need to be checked for revocation.

To seperate the certificates in the certs.file, you can use the `split` utility. For instance:

```bash
$ split -d -a 1 -p "-----BEGIN CERTIFICATE-----" certs.pem crt
```

This will generate a number of files with names starting with "crt":
```bash
$ ls crt*
crt0 crt1 crt2 crt3
```

In this case, 4 files are generated: the attestation certificate (crt0), two intermediates (crt1, crt2), and a root (crt3).
The intermeidates are the ones to check, similar to revoked-crt.pem above.

# Attestation

Note that the public key in the attestation certificate should match the public key retrieved earlier:

```
$ openssl x509 -in attestation.pem -noout -pubkey | openssl pkey -noout -pubin -text
Public-Key: (256 bit)
pub:
    04:e7:ff:40:b4:e4:23:57:d8:89:c5:b1:72:f0:6b:
    18:26:da:ee:73:81:6a:40:8e:70:79:6e:b5:39:ea:
    17:93:f7:5a:25:a5:9a:03:e1:62:fe:1d:9c:eb:7b:
    4b:fd:aa:61:91:4f:26:58:a3:e7:c3:d3:94:8e:67:
    f5:4a:de:90:56
ASN1 OID: prime256v1
NIST CURVE: P-256
```

The attestation certificate carries an X509v3 extension with OID 1.3.6.1.4.1.11129.2.1.17 that contains information about the key pair being attested and the state of the device at key generation time.
It is ASN.1 encoded and can be visualized as follows:

```
$ step certificate inspect certs.pem -format json | jq '.unknown_extensions[] | select (.id == "1.3.6.1.4.1.11129.2.1.17") | .value' -r | base64 -d | der2ascii 
SEQUENCE {
  INTEGER { 300 }
  ENUMERATED { `02` }
  INTEGER { 300 }
  ENUMERATED { `02` }
  OCTET_STRING { `6a881956283068c6353ffbcfeb09d02232aa8074` }
  OCTET_STRING {}
  SEQUENCE {
    [701] {
      INTEGER { `019ac59b6bae` }
    }
    [709] {
      OCTET_STRING {
        SEQUENCE {
          SET {
            SEQUENCE {
              OCTET_STRING { "nl.joostd.signingdemo" }
              INTEGER { 1 }
            }
          }
          SET {
            OCTET_STRING { `fa4c1d8efa8ade32953ad6c84b11f435db8275def6099685361cbfa1577d0895` }
          }
        }
      }
    }
  }
  SEQUENCE {
    [1] {
      SET {
        INTEGER { 2 }
        INTEGER { 3 }
      }
    }
    [2] {
      INTEGER { 3 }
    }
    [3] {
      INTEGER { 256 }
    }
    [5] {
      SET {
        INTEGER { 4 }
      }
    }
    [10] {
      INTEGER { 1 }
    }
    [503] {
      NULL {}
    }
    [509] {
      NULL {}
    }
    [702] {
      INTEGER { 0 }
    }
    [704] {
      SEQUENCE {
        OCTET_STRING { `9ac4174153d45e4545b0f49e22fe63273999b6ac1cb6949c3a9f03ec8807eee9` }
        BOOLEAN { TRUE }
        ENUMERATED { `00` }
        OCTET_STRING { `69c9d4748ac6ff48341fb862ba4eb0d21f19198698d175eef5df183a62195cb3` }
      }
    }
    [705] {
      INTEGER { `027100` }
    }
    [706] {
      INTEGER { `03170d` }
    }
    [718] {
      INTEGER { `01350119` }
    }
    [719] {
      INTEGER { `01350119` }
    }
  }
}
```

See [here](https://source.android.com/docs/security/features/keystore/attestation#schema) for documentation of the schema.

## Converting to JSON

To make the above attestation more readable, convert the KeyDescription to JSON.
See the file [KeyDescription.asn1](KeyDescription.asn1), adapted from [Version 300 of the schmema](https://source.android.com/docs/security/features/keystore/attestation#attestation-v300).

For this, we use [asn1tools](https://pypi.org/project/asn1tools/).

Save the hex-encoded X.509 extension attribute value in an environment variable:

```
$ HEX=$(step certificate inspect certs.pem -format json | jq '.unknown_extensions[] | select (.id == "1.3.6.1.4.1.11129.2.1.17") | .value' -r | base64 -d | xxd -p -c0)
```

Then, convert the Key Description using JSON Encoding Rules (JER):

```
$ asn1tools convert -o jer KeyDescription.asn1 KeyDescription $HEX
{
    "attestationVersion": 300,
    "attestationSecurityLevel": "strongBox",
    "keyMintVersion": 300,
    "keymintSecurityLevel": "strongBox",
    "attestationChallenge": "6A881956283068C6353FFBCFEB09D02232AA8074",
    "uniqueId": "",
    "softwareEnforced": {
        "creationDateTime": 1764251888558,
        "attestationApplicationId": "3042311C301A04156E6C2E6A6F6F7374642E7369676E696E6764656D6F02010131220420FA4C1D8EFA8ADE32953AD6C84B11F435DB8275DEF6099685361CBFA1577D0895"
    },
    "hardwareEnforced": {
        "purpose": [
            2,
            3
        ],
        "algorithm": 3,
        "keySize": 256,
        "digest": [
            4
        ],
        "ecCurve": 1,
        "noAuthRequired": null,
        "unlockedDeviceReq": null,
        "origin": 0,
        "rootOfTrust": {
            "verifiedBootKey": "9AC4174153D45E4545B0F49E22FE63273999B6AC1CB6949C3A9F03EC8807EEE9",
            "deviceLocked": true,
            "verifiedBootState": "verified",
            "verifiedBootHash": "69C9D4748AC6FF48341FB862BA4EB0D21F19198698D175EEF5DF183A62195CB3"
        },
        "osVersion": 160000,
        "osPatchLevel": 202509,
        "vendorPatchLevel": 20250905,
        "bootPatchLevel": 20250905
    }
}
```



See [here](https://source.android.com/docs/security/features/keystore/attestation#keydescription-fields) for a reference of the Key Description fields.
