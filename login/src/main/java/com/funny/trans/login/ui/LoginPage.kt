package com.funny.trans.login.ui

import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.funny.trans.login.R
import com.funny.translation.AppConfig
import com.funny.translation.bean.UserInfoBean
import com.funny.translation.helper.BiometricUtils
import com.funny.translation.helper.UserUtils
import com.funny.translation.helper.VibratorUtils
import com.funny.translation.helper.toastOnUi
import com.funny.translation.ui.MarkdownText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal const val WIDTH_FRACTION = 0.8f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoginPage(
    navController: NavController,
    onLoginSuccess: (UserInfoBean) -> Unit,
) {
    val vm: LoginViewModel = viewModel()
    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.d("GameActivityResult", "LoginScreen: data: ${result.data?.extras}")
        result.data?.getStringExtra("password")?.let {
            Log.d("GameActivityResult", "LoginScreen: password: $it")
            vm.password = it
            vm.passwordType = "2"
        }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        val pagerState = rememberPagerState()
        val scope = rememberCoroutineScope()
        fun changePage(index: Int) = scope.launch {
            pagerState.animateScrollToPage(index)
        }
        TabRow(
            pagerState.currentPage,
            modifier = Modifier
                .fillMaxWidth(WIDTH_FRACTION)
                .clip(shape = RoundedCornerShape(12.dp))
                .background(Color.Transparent),
            containerColor = Color.Transparent
            //backgroundColor = Color.Unspecified
        ) {
            Tab(pagerState.currentPage == 0, onClick = { changePage(0) }) {
                Text(
                    stringResource(R.string.login),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Tab(pagerState.currentPage == 1, onClick = { changePage(1) }) {
                Text(
                    stringResource(R.string.register),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        var privacyGranted by remember { mutableStateOf(false) }
        val shrinkAnim = remember { Animatable(0f) }
        val context = LocalContext.current
        val remindToGrantPrivacyAction = remember {
            {
                scope.launch {
                    intArrayOf(20, 0).forEach {
                        shrinkAnim.animateTo(it.toFloat(), spring(Spring.DampingRatioHighBouncy))
                    }
                }
                VibratorUtils.vibrate(70)
                context.toastOnUi("请先同意隐私政策和用户协议！")
            }
        }

        HorizontalPager(
            pageCount = 2,
            modifier = Modifier.weight(1f),
            state = pagerState,
        ) { page ->
            when (page) {
                0 -> LoginForm(navController, vm, onLoginSuccess = onLoginSuccess, privacyGranted = privacyGranted, remindToGrantPrivacyAction = remindToGrantPrivacyAction)
                1 -> RegisterForm(vm, onRegisterSuccess = { changePage(0) }, privacyGranted = privacyGranted, remindToGrantPrivacyAction = remindToGrantPrivacyAction)
            }
        }
        Row(
            Modifier
                .padding(8.dp)
                .offset { IntOffset(0, shrinkAnim.value.roundToInt()) },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = privacyGranted, onCheckedChange = { privacyGranted = it })
            MarkdownText(
                "我已阅读并同意[隐私政策](https://api.funnysaltyfish.fun/trans/v1/api/privacy)和[用户协议](https://api.funnysaltyfish.fun/trans/v1/api/user_agreement)",
                color = contentColorFor(backgroundColor = MaterialTheme.colorScheme.background).copy(0.8f),
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun LoginForm(
    navController: NavController,
    vm: LoginViewModel,
    privacyGranted: Boolean = false,
    onLoginSuccess: (UserInfoBean) -> Unit = {},
    remindToGrantPrivacyAction: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth(WIDTH_FRACTION)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        InputUsernameWrapper(vm, if (vm.passwordType == "1") ImeAction.Done else ImeAction.Next)
        Spacer(modifier = Modifier.height(12.dp))
        if (vm.shouldVerifyEmailWhenLogin){
            InputEmailWrapper(modifier = Modifier.fillMaxWidth(), vm = vm, initialSent = true)
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (vm.passwordType == "2"){
            InputPasswordWrapper(vm = vm, readonly = false)
        } else CompletableButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    BiometricUtils.validateFingerPrint(
                        context as AppCompatActivity,
                        username = vm.username,
                        did = AppConfig.androidId,
                        onNotSupport = { msg: String -> context.toastOnUi(msg) },
                        onFail = { context.toastOnUi("认证失败！") },
                        onSuccess = { encryptedInfo, iv ->
                            context.toastOnUi("指纹认证成功！")
                            vm.finishValidateFingerPrint = true
                            vm.encryptedInfo = encryptedInfo
                            vm.iv = iv
                        },
                        onError = { errorCode, errorMsg -> context.toastOnUi("认证失败！（$errorCode: $errorMsg）") },
                        onNewFingerPrint = { email ->
                            if(email.isNotEmpty()){
                                try{
                                    scope.launch {
                                        vm.shouldVerifyEmailWhenLogin = true
                                        vm.email = email
                                        BiometricUtils.uploadFingerPrint(username = vm.username)
                                        UserUtils.sendVerifyEmail(vm.username, email)
                                        context.toastOnUi("邮件发送成功，请注意查收！")
                                    }
                                }catch (e: Exception){
                                    context.toastOnUi("邮件发送失败！")
                                }
                            }
                        },
                        onUsePassword = {
                            vm.passwordType = "2"
                            vm.password = ""
                        }
                    )
                } else {
                    context.toastOnUi("您的安卓版本过低，不支持指纹认证！将使用密码认证~", Toast.LENGTH_LONG)
                    vm.passwordType = "2"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = vm.isValidUsername,
            completed = vm.finishValidateFingerPrint
        ) {
            Text("验证指纹")
        }
        ExchangePasswordType(
            passwordType = vm.passwordType
        ) { vm.passwordType = it }
        Spacer(modifier = Modifier.height(12.dp))

        // 因为下面的表达式变化速度快过UI的变化速度，为了减少重组次数，此处使用 derivedStateOf
        val enabledLogin by remember {
            derivedStateOf {
                if (vm.shouldVerifyEmailWhenLogin) {
                    vm.isValidUsername && vm.finishValidateFingerPrint && vm.isValidEmail && vm.verifyCode.length == 6
                } else {
                    when(vm.passwordType){
                        "1" -> vm.isValidUsername && vm.finishValidateFingerPrint
                        "2" -> vm.isValidUsername && UserUtils.isValidPassword(vm.password)
                        else -> false
                    }
                }
            }
        }
        Button(
            onClick = {
                if (!privacyGranted) {
                    remindToGrantPrivacyAction()
                    return@Button
                }
                vm.login(
                    onSuccess = {
                        context.toastOnUi("登录成功！")
                        onLoginSuccess(it)
                    },
                    onError = { msg ->
                        context.toastOnUi("登录失败！（$msg）")
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabledLogin
        ) {
            Text(stringResource(id = R.string.login))
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = {
                    navController.navigate(LoginRoute.FindUsernamePage.route){
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(LoginRoute.LoginPage.route){
                            inclusive = false
                        }
                    }
                }
            ) {
                Text("忘记用户名？")
            }
            TextButton(
                onClick = {
                    navController.navigate(LoginRoute.ResetPasswordPage.route){
                        launchSingleTop = true
                        restoreState = true
                        popUpTo(LoginRoute.LoginPage.route){
                            inclusive = false
                        }
                    }
                }
            ) {
                Text("忘记密码？")
            }
        }
    }
}

@Composable
private fun RegisterForm(
    vm: LoginViewModel,
    privacyGranted: Boolean,
    onRegisterSuccess: () -> Unit = {},
    remindToGrantPrivacyAction: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth(WIDTH_FRACTION)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        InputUsernameWrapper(vm)
        Spacer(modifier = Modifier.height(8.dp))
        InputEmailWrapper(modifier = Modifier.fillMaxWidth(), vm = vm)
        Spacer(modifier = Modifier.height(12.dp))
        if (vm.passwordType == "2"){
            InputPasswordWrapper(vm = vm, readonly = false)
            Spacer(modifier = Modifier.height(8.dp))
        }
        else {
            CompletableButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        BiometricUtils.setFingerPrint(
                            context as AppCompatActivity,
                            username = vm.username,
                            did = AppConfig.androidId,
                            onNotSupport = { msg: String -> context.toastOnUi(msg) },
                            onFail = { context.toastOnUi("设置指纹时失败，原因未知，请换用密码！") },
                            onSuccess = { encryptedInfo, iv ->
                                context.toastOnUi("添加指纹成功！")
                                vm.finishSetFingerPrint = true
                                vm.encryptedInfo = encryptedInfo
                                vm.iv = iv
                            },
                            onError = { errorCode, errorMsg -> context.toastOnUi("认证失败！（$errorCode: $errorMsg）") },
                            onUsePassword = {
                                vm.passwordType = "2"
                                vm.password = ""
                            }
                        )
                    } else {
                        context.toastOnUi("您的安卓版本过低，不支持指纹认证！将使用密码认证~", Toast.LENGTH_LONG)
                        vm.passwordType = "2"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = vm.isValidUsername,
                completed = vm.finishSetFingerPrint
            ) {
                Text("添加指纹")
            }
        }
        ExchangePasswordType(
            passwordType = vm.passwordType
        ) { vm.passwordType = it }
        Spacer(modifier = Modifier.height(12.dp))
        val enableRegister by remember {
            derivedStateOf {
                if(vm.passwordType == "1")
                    vm.isValidUsername && vm.isValidEmail && vm.verifyCode.length == 6 && vm.finishSetFingerPrint
                else
                    vm.isValidUsername && vm.isValidEmail && vm.verifyCode.length == 6 && UserUtils.isValidPassword(vm.password)
            }
        }
        Button(
            onClick = {
                if (!privacyGranted) {
                    remindToGrantPrivacyAction()
                    return@Button
                }
                vm.register(
                    onSuccess = {
                        context.toastOnUi("注册成功！")
                        onRegisterSuccess()
                    },
                    onError = { msg ->
                        context.toastOnUi("注册失败！（$msg）")
                    }
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enableRegister
        ) {
            Text(stringResource(id = R.string.register))
        }
    }
}

@Composable
private fun ExchangePasswordType(
    passwordType: String,
    updatePasswordType: (String) -> Unit
){
    if (passwordType == "2" && !AppConfig.lowerThanM){
        Spacer(modifier = Modifier.height(4.dp))
        Text(modifier = Modifier.clickable { updatePasswordType("1") }, text = "切换为指纹", style = MaterialTheme.typography.labelSmall)
    } else if (passwordType == "1") {
        Spacer(modifier = Modifier.height(4.dp))
        Text(modifier = Modifier.clickable { updatePasswordType("2") }, text = "切换为密码", style = MaterialTheme.typography.labelSmall)
    }
}



@Composable
private fun InputUsernameWrapper(
    vm: LoginViewModel,
    imeAction: ImeAction = ImeAction.Next,
) {
    InputUsername(usernameProvider = vm::username, updateUsername = vm::updateUsername, isValidUsernameProvider = vm::isValidUsername, imeAction = imeAction)
}

@Composable
private fun InputEmailWrapper(
    modifier: Modifier, vm: LoginViewModel, initialSent: Boolean = false
) {
    val context = LocalContext.current
    InputEmail(
        modifier = modifier,
        value = vm.email,
        onValueChange = { vm.email = it },
        isError = vm.email != "" && !vm.isValidEmail,
        verifyCode = vm.verifyCode,
        onVerifyCodeChange = { vm.verifyCode = it },
        initialSent = initialSent,
        onClick = { vm.sendVerifyEmail(context) }
    )
}

@Composable
fun InputEmail(
    modifier: Modifier = Modifier,
    value: String = "",
    onValueChange: (String) -> Unit = {},
    isError: Boolean = false,
    verifyCode: String,
    onVerifyCodeChange: (String) -> Unit = {},
    initialSent: Boolean,
    onClick: () -> Unit
) {
    Column(modifier) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            isError = isError,
            label = { Text(text = "邮箱") },
            placeholder = { Text("请输入主流的合法邮箱") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            trailingIcon = {
                CountDownTimeButton(
                    modifier = Modifier.weight(1f),
                    onClick = onClick,
                    enabled = value != "" && !isError,
                    initialSent = initialSent // 当需要
                )
            },
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = verifyCode,
            onValueChange = onVerifyCodeChange,
            isError = false,
            label = { Text(text = "验证码") },
            placeholder = { Text("请输入收到的验证码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            )
        )
    }
}

@Composable
private fun InputPasswordWrapper(
    vm: LoginViewModel,
    readonly: Boolean
) {
    InputPassword(passwordProvider = vm::password, updatePassword = vm::updatePassword, readonly = readonly)
}

/**
 * 带倒计时的按钮
 *
 */
@Composable
fun CountDownTimeButton(
    modifier: Modifier,
    onClick: () -> Unit,
    countDownTime: Int = 60,
    text: String = "获取验证码",
    enabled: Boolean = true,
    initialSent: Boolean = false
) {
    var time by remember { mutableStateOf(countDownTime) }
    var isTiming by remember { mutableStateOf(initialSent) }
    LaunchedEffect(isTiming) {
        while (isTiming) {
            delay(1000)
            time--
            if (time == 0) {
                isTiming = false
                time = countDownTime
            }
        }
    }
    TextButton(
        onClick = {
            if (!isTiming) {
                isTiming = true
                onClick()
            }
        },
        modifier = modifier,
        enabled = enabled && !isTiming
    ) {
        Text(text = if (isTiming) "${time}s" else text)
    }
}