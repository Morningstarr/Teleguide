package com.mongodb.alliance.ui

import android.Manifest.permission.*
import android.annotation.TargetApi
import android.app.ActionBar
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import cafe.adriel.broker.unsubscribe
import com.cloudinary.Cloudinary
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.cloudinary.android.policy.UploadPolicy
import com.cloudinary.utils.ObjectUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kroegerama.imgpicker.BottomSheetImagePicker
import com.kroegerama.imgpicker.ButtonType
import com.mongodb.alliance.*
import com.mongodb.alliance.adapters.UserDataAdapter
import com.mongodb.alliance.databinding.ActivityProfileBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.*
import com.mongodb.alliance.model.UserData
import com.mongodb.alliance.model.UserDataType
import com.mongodb.alliance.model.UserRealm
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.ui.authorization.LoginActivity
import com.mongodb.alliance.ui.telegram.CodeFragment
import com.mongodb.alliance.ui.telegram.NewPhoneNumberFragment
import com.mongodb.alliance.ui.telegram.PasswordFragment
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.com_mongodb_alliance_model_UserRealmRealmProxy
import io.realm.kotlin.where
import io.realm.mongodb.User
import io.realm.mongodb.sync.SyncConfiguration
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import timber.log.Timber
import java.lang.reflect.Field
import java.lang.reflect.Method
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime


@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class ProfileActivity : AppCompatActivity(), GlobalBroker.Subscriber,
    BottomSheetImagePicker.OnImagesSelectedListener, CoroutineScope {

    @TelegramServ
    @Inject
    lateinit var t_service: Service
    private lateinit var recyclerView: RecyclerView
    private lateinit var binding: ActivityProfileBinding
    private lateinit var realm: Realm
    private lateinit var adapter : UserDataAdapter
    private lateinit var customActionBarView : View
    private lateinit var rootLayout : CoordinatorLayout
    private var bottomSheetFragment: BottomSheetDialogFragment? = null
    private lateinit var chooseImageFragment : BottomSheetDialogFragment
    private lateinit var placeholder : TextView

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    private var permissionsToRequest: ArrayList<String>? = null
    private val permissionsRejected: ArrayList<String> = ArrayList()
    private val permissions: ArrayList<String> = ArrayList()

    private val ALL_PERMISSIONS_RESULT = 107
    private val IMAGE_RESULT = 200

    private var user: User? = null

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(event: NullObjectAccessEvent) {
        Toast.makeText(baseContext, event.message, Toast.LENGTH_SHORT).show()
    }

    @ExperimentalCoroutinesApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        EventBus.getDefault().register(this)
        lifecycleScope.launch {
            (t_service as TelegramService).setUpClient()
        }
        try{
            MediaManager.init(this)
        }
        catch(e:Exception){
            if(!e.message?.contains("already initialized")!!){
                finish()
            }
            Timber.e(e.message)
        }
        subscribe<ChangeUserDataEvent>(lifecycleScope, emitRetained = true) { event ->
            when(event.parameter) {
                1 -> {
                    val builder =
                        AlertDialog.Builder(this)

                    builder.setTitle("Warning")
                    builder.setMessage("Are you sure you want to change your phone number? It will lead to disconnection of current telegram account!")

                    builder.setPositiveButton(
                        "YES"
                    ) { dialog, which ->
                        lifecycleScope.launch {
                            withContext(Dispatchers.IO) {
                                (t_service as TelegramService).logOut()
                            }
                            withContext(Dispatchers.Main) {
                                setUpRecyclerView()
                            }
                            withContext(Dispatchers.IO) {
                                (t_service as TelegramService).setUpClient()
                                (t_service as TelegramService).initService()
                            }
                        }

                    }
                    builder.setNegativeButton(
                        "NO"
                    ) { dialog, which -> // Do nothing
                        dialog.dismiss()
                    }

                    val alert = builder.create()
                    alert.show()
                }
                2 -> {
                    if((t_service as TelegramService).isUser() != null) {
                        val builder =
                            AlertDialog.Builder(this)

                        builder.setTitle("Warning")
                        builder.setMessage("Are you sure you want to change your telegram account?")
                        builder.setPositiveButton(
                            "YES"
                        ) { dialog, which ->
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    (t_service as TelegramService).logOut()
                                }
                                withContext(Dispatchers.Main) {
                                    setUpRecyclerView()
                                }
                                withContext(Dispatchers.IO) {
                                    (t_service as TelegramService).setUpClient()
                                    (t_service as TelegramService).initService()
                                }
                            }
                        }
                        builder.setNegativeButton(
                            "NO"
                        ) { dialog, which -> // Do nothing
                            dialog.dismiss()
                        }

                        val alert = builder.create()
                        alert.show()
                    }
                    else{
                        lifecycleScope.launch {
                            val task = async {
                                withContext(Dispatchers.IO) {
                                    (t_service as TelegramService).returnClientState()
                                }
                            }
                            val state = task.await()
                            when (state) {
                                ClientState.ready -> {
                                    Toast.makeText(baseContext, "state ready", Toast.LENGTH_SHORT).show()
                                }
                                ClientState.waitNumber, ClientState.waitPassword, ClientState.waitCode -> {
                                    (t_service as TelegramService).initService()
                                }
                                else -> {
                                    Toast.makeText(baseContext, "unknown state", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }
                3 -> {
                    val builder =
                        AlertDialog.Builder(this)

                    builder.setTitle("Warning")
                    builder.setMessage("Are you sure you want to change your account password? It can't be undone!")

                    builder.setPositiveButton(
                        "YES"
                    ) { dialog, which -> // Do nothing but close the dialog
                        realm = Realm.getDefaultInstance()
                        val appUserRealm = realm.where<UserRealm>()
                            .equalTo("user_id", channelApp.currentUser()?.id)
                            .findFirst() as UserRealm
                        val userEmail =
                            (appUserRealm as com_mongodb_alliance_model_UserRealmRealmProxy).`realmGet$name`()
                        channelApp.emailPassword.sendResetPasswordEmailAsync(userEmail) {
                            if (it.isSuccess) {
                                Toast.makeText(
                                    this,
                                    "Successfully sent the user a reset password link to $userEmail",
                                    Toast.LENGTH_LONG
                                ).show().also { finish() }
                            } else {
                                Toast.makeText(
                                    this,
                                    "Failed to send the user a reset password link to $userEmail",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }

                    builder.setNegativeButton(
                        "NO"
                    ) { dialog, which -> // Do nothing
                        dialog.dismiss()
                    }

                    val alert = builder.create()
                    alert.show()
                }
            }
        }

        otherEventsSubscription()
        telegramConnectedSubscription()


        permissions.add(CAMERA);
        permissions.add(WRITE_EXTERNAL_STORAGE);
        permissions.add(READ_EXTERNAL_STORAGE);
        permissionsToRequest = findUnAskedPermissions(permissions);

        val actionbar = supportActionBar
        if (actionbar != null) {
            actionbar.displayOptions = ActionBar.DISPLAY_SHOW_CUSTOM
            actionbar.setDisplayShowCustomEnabled(true)
            actionbar.setCustomView(R.layout.action_bar_drawable)
            customActionBarView = actionbar.customView
        }

        val fab = binding.chooseImageFab
        fab.setOnClickListener{
            val builder =
                BottomSheetImagePicker.Builder("com.mongodb.alliance.fileprovider")
                    .cameraButton(ButtonType.Tile)            //style of the camera link (Button in header, Image tile, None)
                    .galleryButton(ButtonType.Tile)           //style of the gallery link
                    .singleSelectTitle(R.string.pick_single)    //header text
                    .peekHeight(R.dimen.peekHeight)             //peek height of the bottom sheet
                    .columnSize(R.dimen.columnSize)             //size of the columns (will be changed a little to fit)
                    .requestTag("single")

            chooseImageFragment = builder.build()

            chooseImageFragment.setStyle(DialogFragment.STYLE_NORMAL,
                R.style.ImagePickerTheme
            )

            lifecycleScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (permissionsToRequest?.size!! > 0) {
                        permissionsToRequest?.toArray(Array<String>(permissionsToRequest!!.size) { i -> permissionsToRequest!![i] })
                            ?.let { requestPermissions(it, ALL_PERMISSIONS_RESULT) }
                    }
                    else{
                        chooseImageFragment.show(supportFragmentManager, "single")
                    }

                }
            }

        }

        customActionBarView.findViewById<ImageButton>(R.id.actionBar_button_back).setOnClickListener {
            finish()
        }
        customActionBarView.findViewById<ImageButton>(R.id.actionBar_button_menu).setOnClickListener {
            rootLayout = binding.coordinatorProfile
            val anchor = binding.anchorProfile
            val wrapper = ContextThemeWrapper(this,
                R.style.MyPopupMenu
            )
            val popup = PopupMenu(wrapper, anchor, Gravity.END)

            try {
                val fields: Array<Field> = popup.javaClass.declaredFields
                for (field in fields) {
                    if ("mPopup" == field.getName()) {
                        field.setAccessible(true)
                        val menuPopupHelper: Any = field.get(popup)
                        val classPopupHelper =
                            Class.forName(menuPopupHelper.javaClass.name)
                        val setForceIcons: Method = classPopupHelper.getMethod(
                            "setForceShowIcon",
                            Boolean::class.javaPrimitiveType
                        )
                        setForceIcons.invoke(menuPopupHelper, true)
                        break
                    }
                }
            } catch (e: Exception) {
                Timber.e(e.message)
            }

            popup.menuInflater.inflate(R.menu.profile_menu, popup.menu)
            popup.show()

            popup.setOnMenuItemClickListener { item ->
                when(item.itemId){
                    R.id.profile_action_exit -> {
                        this.finishAffinity()
                    }
                    R.id.profile_action_choose_img -> {
                        val builder =
                            BottomSheetImagePicker.Builder("com.mongodb.alliance.fileprovider")
                                .cameraButton(ButtonType.Tile)            //style of the camera link (Button in header, Image tile, None)
                                .galleryButton(ButtonType.Tile)           //style of the gallery link
                                .singleSelectTitle(R.string.pick_single)    //header text
                                .peekHeight(R.dimen.peekHeight)             //peek height of the bottom sheet
                                .columnSize(R.dimen.columnSize)             //size of the columns (will be changed a little to fit)
                                .requestTag("single")

                        chooseImageFragment = builder.build()

                        chooseImageFragment.setStyle(DialogFragment.STYLE_NORMAL,
                            R.style.ImagePickerTheme
                        )

                        lifecycleScope.launch {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                if (permissionsToRequest?.size!! > 0) {
                                    permissionsToRequest?.toArray(Array<String>(permissionsToRequest!!.size) { i -> permissionsToRequest!![i] })
                                        ?.let { requestPermissions(it, ALL_PERMISSIONS_RESULT) }
                                }
                                else{
                                    chooseImageFragment.show(supportFragmentManager, "single")
                                }
                            }
                        }
                    }
                    R.id.profile_action_delete_img -> {
                        val builder =
                            AlertDialog.Builder(this)

                        builder.setTitle("Внимание")
                        builder.setMessage("Вы уверены, что хотите удалить фото?")

                        builder.setPositiveButton(
                            "YES"
                        ) { dialog, which -> // Do nothing but close the dialog
                            deleteImage(true)
                        }

                        builder.setNegativeButton(
                            "NO"
                        ) { dialog, which -> // Do nothing
                            dialog.dismiss()
                        }

                        val alert = builder.create()
                        alert.show()
                    }
                }
                true
            }
        }

        val view = binding.root
        setContentView(view)
        recyclerView = binding.userDataRecView
        placeholder = binding.profilePlaceholder

    }

    override fun onStart() {
        super.onStart()

        val action: String? = intent?.action
        val data: Uri? = intent?.data

        if(data != null){
            var token = data.toString().subSequence(data.toString().lastIndexOf("/") + 7, data.toString().lastIndexOf("&")).toString()
            var tokenId = data.toString().subSequence(data.toString().lastIndexOf("&") + 1, data.toString().length).toString()
            token = token.drop(6)
            tokenId = tokenId.drop(8)
            bottomSheetFragment =
                NewPasswordFragment(token, tokenId)

            (bottomSheetFragment as NewPasswordFragment).show(
                this.supportFragmentManager,
                (bottomSheetFragment as NewPasswordFragment).tag
            )
        }

        try {
            user = channelApp.currentUser()
        } catch (e: IllegalStateException) {
            Timber.e(e.message)
        }
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
        } else {
            realm = Realm.getDefaultInstance()

            val config = SyncConfiguration.Builder(user, user?.id)
                .waitForInitialRemoteData()
                .build()

            Realm.setDefaultConfiguration(config)

            try {
                Realm.getInstanceAsync(config, object : Realm.Callback() {
                    override fun onSuccess(realm: Realm) {
                        // since this realm should live exactly as long as this activity, assign the realm to a member variable
                        this@ProfileActivity.realm = realm
                    }
                })
            } catch (e: Exception) {
                Timber.e(e.message)
            }
        }

        setUpRecyclerView()

        try {
            
            val appUserRealm = realm.where<UserRealm>().equalTo("user_id", user?.id).findFirst() as UserRealm
            placeholder.text = appUserRealm.name.subSequence(0, 3)
            val img = (appUserRealm as com_mongodb_alliance_model_UserRealmRealmProxy).`realmGet$image`() //directly get value of some field
            if(img != null) {
                Picasso.get()
                    .load(MediaManager.get().url().generate(appUserRealm.image.toString()))
                    .into(binding.mainBackdrop)
            }
            else{
                placeholder.visibility = View.VISIBLE
            }
        }
        catch(e:Exception){
            placeholder.visibility = View.VISIBLE
        }

    }

    private fun setUpRecyclerView() {
        var userDataArray = ArrayList<UserData>()
        val appUserRealm = realm.where<UserRealm>().equalTo("user_id", user?.id).findFirst() as UserRealm
        userDataArray.add(UserData(appUserRealm.name,"Ваш электронный адрес", UserDataType.email))
        userDataArray.add(checkNumber())
        userDataArray.add(checkTelegram())
        userDataArray.add(UserData("123123123", "Нажмите, чтобы изменить пароль", UserDataType.password))
        /*userDataArray.add(UserData("123123123", "Нажмите, чтобы изменить пароль", UserDataType.password))
        userDataArray.add(UserData("YourMorningstar", "Нажмите, чтобы изменить телеграм аккаунт", UserDataType.telegramAccount))
        userDataArray.add(UserData("kirill_kovrik@mail.ru","Нажмите, чтобы изменить почту", UserDataType.email))
        userDataArray.add(UserData("+380 71 331 2170", "Нажмите, чтобы изменить номер телефона", UserDataType.phoneNumber))*/

        adapter = UserDataAdapter(userDataArray)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)
        recyclerView.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onImagesSelected(uris: List<Uri>, tag: String?) {
        try {
            val config = HashMap<String, String>()
            config["cloud_name"] = "dbtelecloud"

            val requestId = MediaManager.get().upload(
                FileUtils.getPath(
                    baseContext,
                    uris[0]
                )
            )
                .unsigned("klbmit6h")
                .policy(UploadPolicy.Builder()
                    .maxRetries(3).build())
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {
                        // your code here
                    }
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                        val progress = bytes.toDouble() / totalBytes
                    }
                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val appUserRealm = realm.where<UserRealm>().equalTo("user_id", user?.id).findFirst()
                        val imageUrl = appUserRealm?.image
                        if(imageUrl != null){
                            Picasso.get().invalidate(imageUrl)
                            deleteImage(false, imageUrl)
                        }
                        Picasso.get()
                            .load(MediaManager.get().url().generate(resultData["url"] as String))
                            .into(binding.mainBackdrop)
                        realm.executeTransaction {
                            appUserRealm?.image = resultData["url"] as String
                        }

                        placeholder.visibility = View.GONE
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        Toast.makeText(baseContext, error.description, Toast.LENGTH_SHORT).show()
                        placeholder.visibility = View.VISIBLE
                    }
                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        // your code here
                    }
                })
                .startNow(this)
        }
        catch(e:Exception){
            Timber.e(e.message)
        }

    }

    private fun findUnAskedPermissions(wanted: ArrayList<String>): ArrayList<String>? {
        val result = ArrayList<String>()
        for (perm in wanted) {
            if (!hasPermission(perm)) {
                result.add(perm)
            }
        }
        return result
    }

    private fun hasPermission(permission: String): Boolean {
        if (canMakeSmores()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            }
        }
        return true
    }

    private fun canMakeSmores(): Boolean {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1
    }

    @TargetApi(Build.VERSION_CODES.M)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        when (requestCode) {
            ALL_PERMISSIONS_RESULT -> {
                for (perms in permissionsToRequest!!) {
                    if (!hasPermission(perms)) {
                        permissionsRejected.add(perms)
                    }
                }
                if (permissionsRejected.size > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (shouldShowRequestPermissionRationale(permissionsRejected[0])) {
                            showMessageOKCancel("These permissions are mandatory for the application. Please allow access.",
                                DialogInterface.OnClickListener { dialog, which ->
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                        requestPermissions(
                                            permissionsRejected.toTypedArray(),
                                            ALL_PERMISSIONS_RESULT
                                        )
                                    }
                                })
                            return
                        }
                    }
                }
                else{
                    chooseImageFragment.show(supportFragmentManager, "single")
                }
            }
        }
    }

    private fun showMessageOKCancel(message: String, okListener: DialogInterface.OnClickListener) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("OK", okListener)
            .setNegativeButton("Cancel", null)
            .create()
            .show()
    }

    private fun checkTelegram() : UserData{
        var isNotFinishedConnection : Boolean = false
        var username = "default"
        runBlocking {
            val taskName = async {
                (t_service as TelegramService).getProfileName()
            }
            username = taskName.await()
        }

        if(username == ""){
            return UserData("Your account has no username", "Нажмите, чтобы изменить телеграм аккаунт", UserDataType.telegramAccount)
        }
        if(username != " "){
            return UserData(username, "Нажмите, чтобы изменить телеграм аккаунт", UserDataType.telegramAccount)
        }
        else {
            runBlocking {
                val task = async {
                    withContext(Dispatchers.IO) {
                        (t_service as TelegramService).returnClientState()
                    }
                }
                val state = task.await()
                when (state) {
                    ClientState.waitPassword, ClientState.waitCode -> {
                        isNotFinishedConnection = true
                    }
                    ClientState.waitNumber -> {
                        isNotFinishedConnection = false
                    }
                }
            }
            if (!isNotFinishedConnection) {
                return UserData(
                    "",
                    "Нажмите, чтобы добавить телеграм аккаунт",
                    UserDataType.telegramAccount
                )
            } else {
                return UserData(
                    "",
                    "Нажмите, чтоб завершить вход в телеграм",
                    UserDataType.telegramAccount
                )
            }
        }
    }

    private fun checkNumber() : UserData{
        var number = "default"
        runBlocking {
            val taskName = async {
                (t_service as TelegramService).getPhoneNumber()
            }
            number = taskName.await()
        }

        if(number != ""){
            if(number[0] != '+') {
                number = "+$number"
            }
            return UserData(number, "Нажмите, чтобы изменить номер телефона", UserDataType.phoneNumber)
        }
        else{
            return UserData("", "Добавьте телеграм аккаунт для отображения номера телефона", UserDataType.phoneNumber, true)
        }

    }

    private fun deleteImage(showMessage: Boolean, url: String? = null){
        try {
            var publicId = ""
            var imageUrl = url
            realm = Realm.getDefaultInstance()
            val appUserRealm = realm.where<UserRealm>().equalTo("user_id", channelApp.currentUser()?.id).findFirst()
            if(appUserRealm != null) {
                if(imageUrl == null) {
                    imageUrl = appUserRealm.image
                }
                if(imageUrl != null) {
                    publicId = imageUrl.subSequence(imageUrl.lastIndexOf("/") + 1, imageUrl.lastIndexOf(".")).toString()
                }
                else{
                    Toast.makeText(baseContext, "Error! Nothing to delete", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            else{
                Toast.makeText(baseContext, "User not found!", Toast.LENGTH_SHORT).show()
            }
            lateinit var response : String
            binding.mainBackdrop.setImageResource(0)
            placeholder.visibility = View.VISIBLE
            val app: ApplicationInfo = applicationContext.packageManager
                .getApplicationInfo(
                    applicationContext.packageName,
                    PackageManager.GET_META_DATA
                )
            val bundle: Bundle = app.metaData
            val cloudinary =
                Cloudinary(bundle.getString("CLOUDINARY_URL"))
            val deleteParams = ObjectUtils.asMap("invalidate", true)
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val deleteTask = async {
                        cloudinary.uploader().destroy(
                            publicId,
                            deleteParams
                        ).toString()
                    }
                    response = deleteTask.await()
                }
                if(response.contains("ok")){
                    if(showMessage){
                        Toast.makeText(baseContext, "Deleted successfully!", Toast.LENGTH_SHORT)
                            .show()
                        realm.executeTransaction {
                            appUserRealm?.image = null
                        }
                        Picasso.get().invalidate(imageUrl)
                    }
                }
                else{
                    Toast.makeText(baseContext, "An error occured while deleting a picture! Please, try again", Toast.LENGTH_SHORT).show()
                }
            }

        }
        catch(e:Exception){
            Toast.makeText(baseContext, e.message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun telegramConnectedSubscription() {
        subscribe<TelegramConnectedEvent>(lifecycleScope, emitRetained = true) { event ->
            setUpRecyclerView()
            //Toast.makeText(baseContext, "Telegram connected event", Toast.LENGTH_SHORT)
            /*if(newAccount){
                finish()
            }*/
        }
    }

    override fun onStop() {
        super.onStop()
        unsubscribe()
    }

    private fun otherEventsSubscription() {
        subscribe<StateChangedEvent>(lifecycleScope, emitRetained = true) { event ->
            Timber.d("State changed")
            when (event.clientState) {
                ClientState.waitNumber -> {
                    bottomSheetFragment =
                        NewPhoneNumberFragment()

                    (bottomSheetFragment as NewPhoneNumberFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as NewPhoneNumberFragment).tag
                    )
                }
                ClientState.waitCode -> {
                    //bottomSheetFragment?.dismiss()
                    bottomSheetFragment =
                        CodeFragment()

                    (bottomSheetFragment as CodeFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as CodeFragment).tag
                    )
                }
                ClientState.waitPassword -> {
                    //bottomSheetFragment?.dismiss()
                    bottomSheetFragment =
                        PasswordFragment()
                    (bottomSheetFragment as PasswordFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as PasswordFragment).tag
                    )
                }
                else -> {

                }
            }
        }
    }
}