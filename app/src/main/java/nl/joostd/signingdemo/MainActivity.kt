package nl.joostd.signingdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import nl.joostd.signingdemo.ui.theme.SigningDemoTheme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec

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
    var keyPair by remember { mutableStateOf<KeyPair?>(null) }
    var signatureData by remember { mutableStateOf<ByteArray?>(null) }
    var statusMessage by remember { mutableStateOf("App Started. Click 'Generate Keys' to begin.") }

    // This is the data we will sign
    val messageToSign = "Hello, ECDSA! This message will be signed.".toByteArray()

    // Helper function to convert ByteArray to a readable Hex String
    fun ByteArray.toHexString(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte) }


    // --- Cryptographic Functions ---

    fun generateKeys() {
        try {
            // Get an instance of KeyPairGenerator for EC (Elliptic Curve)
            val kpg = KeyPairGenerator.getInstance("EC")

            // Initialize it with the P-256 curve (also known as secp256r1 or prime256v1)
            val ecSpec = ECGenParameterSpec("secp256r1")
            kpg.initialize(ecSpec, SecureRandom())

            // Generate the key pair
            keyPair = kpg.generateKeyPair()

            // Reset signature if new keys are generated
            signatureData = null
            statusMessage = "✅ New EC P256 KeyPair generated!"
        } catch (e: Exception) {
            statusMessage = "❌ Error generating keys: ${e.message}"
        }
    }

    fun signData() {
        val privateKey = keyPair?.private
        if (privateKey == null) {
            statusMessage = "❌ Error: Private key not found. Generate keys first."
            return
        }

        try {
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
        val publicKey = keyPair?.public
        val sig = signatureData

        if (publicKey == null || sig == null) {
            statusMessage = "❌ Error: Public key or signature not found. Generate keys and sign first."
            return
        }

        try {
            // Get an instance of Signature for SHA256withECDSA
            val ecdsaVerify = Signature.getInstance("SHA256withECDSA")

            // Initialize for verification with the public key
            ecdsaVerify.initVerify(publicKey)

            // Pass in the *original* data
            ecdsaVerify.update(messageToSign)

            // Verify the signature
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
                Text("1. Generate EC P256 Keys")
            }

            // Button 2: Sign Message
            Button(
                onClick = { signData() },
                enabled = (keyPair != null), // Only enable if keys exist
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
                text = keyPair?.public?.let {
                    // Just show the beginning of the key for brevity
                    it.toString().take(100) + "..."
                } ?: "N/A",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}