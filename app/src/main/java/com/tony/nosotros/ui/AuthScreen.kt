package com.tony.nosotros.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cuenta con correo y contraseña: así la vinculación y los datos sobreviven si se reinstala la app.
 * [upgrade] = este teléfono ya estaba en uso sin cuenta; solo falta ponerle correo.
 */
@Composable
fun AuthScreen(vm: MainViewModel, upgrade: Boolean) {
    val busy by vm.authBusy.collectAsState()
    val error by vm.authError.collectAsState()
    var create by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val ok = email.contains("@") && pass.length >= 6 && !busy

    Column(
        Modifier.fillMaxSize().background(C.bg).statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(if (upgrade) "Protege tu cuenta" else if (create) "Crea tu cuenta" else "Inicia sesión", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (upgrade) "Ponle correo y contraseña a esta app. Así, si la borras o cambias de teléfono, entras con tu correo y todo sigue igual."
            else "Cada uno entra con su propio correo. Si reinstalas la app, inicias sesión y recuperas tu vinculación, lugares y fotos.",
            color = C.sub, fontSize = 15.sp,
        )
        OutlinedTextField(email, { email = it.trim() }, label = { Text("Correo") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        OutlinedTextField(pass, { pass = it }, label = { Text("Contraseña (mínimo 6)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        error?.let { Text(it, color = C.danger, fontSize = 14.sp) }
        PrimaryButton(if (busy) "Un momento…" else if (create || upgrade) "Crear cuenta" else "Entrar", enabled = ok) {
            vm.auth(email, pass, create || upgrade)
        }
        if (upgrade) TextButton(onClick = { vm.authSkipped.value = true }) { Text("Más tarde", color = C.sub) }
        else {
            TextButton(onClick = { create = !create; vm.authError.value = null }) {
                Text(if (create) "Ya tengo cuenta: iniciar sesión" else "No tengo cuenta: crear una", color = C.me)
            }
            if (!create) TextButton(onClick = { vm.forgot(email) }, enabled = email.contains("@")) {
                Text("Olvidé mi contraseña", color = C.sub)
            }
        }
    }
}
