# Signing Demo

A simple Android app that demonstrates the use of the Android Keystore for protecting cryptographic keys.
Validation of key attestations is typically done using a backend server. 
For this demo however, we simply log everything and validate by hand (see below).

# Install from source

- clone this repository

```bash
git clone git@github.com:joostd/android-signingdemo.git
cd android-signingdemo/
```

- connect an Android device (in debug mode)

- build and install

```bash
./gradlew installDebug
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
wget https://github.com/joostd/android-signingdemo/releases/download/v0.0.1-alpha/app-debug.apk
adb install app-debug.apk
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
adb logcat -v raw -s attest
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
grep -v '#' attest.log | while read line; do echo $line | xxd -r -p | openssl x509; done > certs.pem
```

The first certificate is the attestation, followed by the chain (in leaf to root order, i.e. the last certificate is the root).
Extract the attestation certificate using OpenSSL:

```
openssl x509 -in certs.pem -out attestation.pem
```

(Note that `openssl x509` only considers the first certificate in the file, which is the attestation certificate)

The root certificate must be one of the well-known roots, and they can be retrieved as follows:

```
curl https://android.googleapis.com/attestation/root | jq .[] -r > roots.pem
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
