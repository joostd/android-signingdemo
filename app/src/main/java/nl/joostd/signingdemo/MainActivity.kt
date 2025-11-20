package nl.joostd.signingdemo

import android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE
import android.os.Build
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProperties.ORIGIN_GENERATED
import android.security.keystore.KeyProperties.SECURITY_LEVEL_STRONGBOX
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nl.joostd.signingdemo.ui.theme.SigningDemoTheme
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature

private const val KEY_ALIAS = "my_ecdsa_key"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SigningDemoTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    CryptoScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SigningDemoTheme {
        CryptoScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoScreen(
    modifier: Modifier = Modifier
) {
    // 1. State variables to hold keys, signature, and status messages
    var publicKey by remember { mutableStateOf<PublicKey?>(null) }
    var signatureData by remember { mutableStateOf<ByteArray?>(null) }
    var attestation by remember { mutableStateOf<ByteArray?>(null) }
    var statusMessage by remember { mutableStateOf("App Started. Click 'Generate Keys' to begin.") }
    val hasStrongbox = LocalContext.current.packageManager.hasSystemFeature(FEATURE_STRONGBOX_KEYSTORE)

    // This is the data we will sign
    val messageToSign = "Hello, ECDSA! This message will be signed.".toByteArray()

    // Helper function to convert ByteArray to a readable Hex String
    fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }


    // --- Cryptographic Functions ---

    @RequiresApi(Build.VERSION_CODES.S)
    fun generateKeys() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null) // Load the keystore
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
            Log.d("keygen", "keystore size: " +  keyStore.size())

            val rnd = SecureRandom()
            val challenge = ByteArray(20)
            rnd.nextBytes(challenge)

            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                // P-256 is used by default for SHA256withECDSA
                .setIsStrongBoxBacked(hasStrongbox) // if possible, this key should be protected by a StrongBox security chip.
                .setUserPresenceRequired(false)
                .setUserConfirmationRequired(false)
                .setUnlockedDeviceRequired(true)
                .setUserAuthenticationRequired(false)
                .setAttestationChallenge(challenge)
                .build()
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                "AndroidKeyStore"
            )
            kpg.initialize(spec)
            val kp = kpg.generateKeyPair()
            publicKey = kp.public // Save the public key to our state
            Log.d("keygen", "Public Key: " + publicKey?.encoded?.toHexString())

            val key = kp.private
            val factory : KeyFactory = KeyFactory.getInstance(key.algorithm, "AndroidKeyStore");
            try {
                val keyInfo: KeyInfo = factory.getKeySpec(key, KeyInfo::class.java)
                Log.d("keygen", "origin generated: " + if (keyInfo.origin == ORIGIN_GENERATED) "yes" else "no")
                Log.d("keygen", "securityLevel: " + keyInfo.securityLevel)
                Log.d("keygen", "strongbox: " + if (keyInfo.securityLevel == SECURITY_LEVEL_STRONGBOX) "yes" else "no")
            } catch (e: Exception) {
                // Not an Android KeyStore key.
            }
            // Reset signature if new keys are generated
            signatureData = null
            attestation = null
            statusMessage = "✅ New EC P256 KeyPair generated!"
        } catch (e: Exception) {
            statusMessage = "❌ Error generating keys: ${e.message}"
        }
    }

    fun signData() {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val entry = keyStore.getEntry(KEY_ALIAS, null)
            if (entry !is KeyStore.PrivateKeyEntry) {
                statusMessage = "❌ Error: Key not found. Generate keys first."
                return
            }
            val privateKey = entry.privateKey // This is a HANDLE, not the raw key

            // Get an instance of Signature for SHA256withECDSA
            val ecdsaSign = Signature.getInstance("SHA256withECDSA")

            // Initialize for signing with the private key
            ecdsaSign.initSign(privateKey)

            // Pass in the data to be signed
            ecdsaSign.update(messageToSign)

            // Generate the signature
            signatureData = ecdsaSign.sign()
            statusMessage = "✅ Message signed successfully!"
        } catch (e: Exception) {
            statusMessage = "❌ Error signing data: ${e.message}"
        }
    }

    fun verifySignature() {
        val sig = signatureData

        if (publicKey == null || sig == null) {
            statusMessage = "❌ Error: Public key or signature not found. Generate keys and sign first."
            return
        }

        try {
            val ecdsaVerify = Signature.getInstance("SHA256withECDSA")
            ecdsaVerify.initVerify(publicKey)
            ecdsaVerify.update(messageToSign)
            val isVerified = ecdsaVerify.verify(sig)
            statusMessage = if (isVerified) {
                "✅ Signature Verified: TRUE"
            } else {
                "❌ Signature Verified: FALSE"
            }
        } catch (e: Exception) {
            statusMessage = "❌ Error verifying signature: ${e.message}"
        }
    }

    fun attestKey() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null) // Load the keystore
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            statusMessage = "❌ Error: Public key not found. Generate keys first."
        }
        val certificates = keyStore.getCertificateChain(KEY_ALIAS)
        Log.d("attest", "#certs: " + certificates.size)
        attestation = certificates[0].encoded
        certificates.forEach { c -> Log.d("attest", c.encoded.toHexString()) }
        statusMessage = if (certificates.size < 1) "❌ Error attesting key" else "✅ attestation type: " + certificates[0].type
    }

    // --- UI Layout ---

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ECDSA P256 Demo") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
        ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Button 1: Generate Keys
            Button(
                onClick = { generateKeys() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("1. Generate EC P256 Key pair")
            }

            // Button 2: Sign Message
            Button(
                onClick = { signData() },
                enabled = (publicKey != null), // Only enable if keys exist
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("2. Sign Message")
            }

            // Button 3: Verify Signature
            Button(
                onClick = { verifySignature() },
                enabled = (signatureData != null), // Only enable if signature exists
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("3. Verify Signature")
            }

            // Button 4: Attest Key
            Button(
                onClick = { attestKey() },
                enabled = (publicKey != null), // Only enable if keys exist
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("4. Attest Key")
            }

            Spacer(Modifier.height(16.dp))

            // --- Status and Info Display ---

            Text(
                text = "Status:",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = if (statusMessage.startsWith("❌")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Public Key:",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = publicKey?.encoded?.toHexString() ?: "N/A",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Signature (Hex):",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = signatureData?.toHexString() ?: "N/A",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Attetation (Hex):",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = attestation?.toHexString() ?: "N/A",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))
            displayHardware()
        }
    }
}

@Composable
fun displayHardware() {
    val context = LocalContext.current
    val hasStrongbox = context.packageManager.hasSystemFeature(FEATURE_STRONGBOX_KEYSTORE)
    val txt = if (hasStrongbox) "✅ Strongbox supported!" else "❌ Strongbox not supported"
    Text(
        text = txt,
        modifier = Modifier.padding(5.dp),
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )
}
