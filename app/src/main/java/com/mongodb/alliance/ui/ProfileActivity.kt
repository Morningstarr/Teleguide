package com.mongodb.alliance.ui

import android.Manifest.permission.*
import android.annotation.TargetApi
import android.app.ActionBar
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
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
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import com.cloudinary.android.policy.UploadPolicy
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kroegerama.imgpicker.BottomSheetImagePicker
import com.kroegerama.imgpicker.ButtonType
import com.mongodb.alliance.*
import com.mongodb.alliance.adapters.UserDataAdapter
import com.mongodb.alliance.databinding.ActivityProfileBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.ChangeUserDataEvent
import com.mongodb.alliance.model.UserData
import com.mongodb.alliance.model.UserDataType
import com.mongodb.alliance.model.UserRealm
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.ui.authorization.LoginActivity
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.com_mongodb_alliance_model_UserRealmRealmProxy
import io.realm.kotlin.where
import io.realm.mongodb.App
import io.realm.mongodb.User
import io.realm.mongodb.mongo.MongoClient
import io.realm.mongodb.mongo.MongoCollection
import io.realm.mongodb.sync.SyncConfiguration
import kotlinx.coroutines.*
import org.bson.Document
import timber.log.Timber
import java.lang.reflect.Field
import java.lang.reflect.Method
import javax.inject.Inject
import kotlin.time.ExperimentalTime


@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class ProfileActivity : AppCompatActivity(), GlobalBroker.Subscriber,
    BottomSheetImagePicker.OnImagesSelectedListener {

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

    private var permissionsToRequest: ArrayList<String>? = null
    private val permissionsRejected: ArrayList<String> = ArrayList()
    private val permissions: ArrayList<String> = ArrayList()

    private val ALL_PERMISSIONS_RESULT = 107
    private val IMAGE_RESULT = 200

    private var user: User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)

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
            when(event.parameter){
                0 -> {
                    bottomSheetFragment =
                        NewEmailFragment(event.data)

                    (bottomSheetFragment as NewEmailFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as NewEmailFragment).tag
                    )
                }
                1 -> {
                    bottomSheetFragment =
                        NewPhoneNumberFragment()

                    (bottomSheetFragment as NewPhoneNumberFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as NewPhoneNumberFragment).tag
                    )
                }
                2 -> {
                    bottomSheetFragment =
                        NewPhoneNumberFragment()

                    (bottomSheetFragment as NewPhoneNumberFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as NewPhoneNumberFragment).tag
                    )
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
                        val appUserRealm = realm.where<UserRealm>().equalTo("user_id", channelApp.currentUser()?.id).findFirst() as UserRealm
                        val userEmail = (appUserRealm as com_mongodb_alliance_model_UserRealmRealmProxy).`realmGet$name`()
                        channelApp.emailPasswordAuth.sendResetPasswordEmailAsync(userEmail) {
                            if (it.isSuccess) {
                                Toast.makeText(this, "Successfully sent the user a reset password link to $userEmail", Toast.LENGTH_LONG).show().also { finish() }
                            } else {
                                Toast.makeText(this, "Failed to send the user a reset password link to $userEmail", Toast.LENGTH_LONG).show()
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
        }

        val view = binding.root
        setContentView(view)
        recyclerView = binding.userDataRecView
        placeholder = binding.profilePlaceholder
        lifecycleScope.launch {
            val task = async {
                withContext(Dispatchers.IO) {
                    (t_service as TelegramService).returnClientState()
                }
            }
            val state = task.await()
            when (state) {
                ClientState.ready -> {
                    val taskNumber = async {
                        (t_service as TelegramService).getPhoneNumber()
                    }
                    val number = taskNumber.await()
                    //binding.profNumber.text = number

                    val taskName = async {
                        (t_service as TelegramService).getProfileName()
                    }
                    val username = taskName.await()
                    //binding.profTgAccount.text = username
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        val action: String? = intent?.action
        val data: Uri? = intent?.data

        if(data != null){
            val token = data.toString().subSequence(data.toString().lastIndexOf("/") + 7, data.toString().lastIndexOf("&")).toString()
            val tokenId = data.toString().subSequence(data.toString().lastIndexOf("&") + 1, data.toString().length).toString()
            bottomSheetFragment =
                NewPasswordFragment(token, tokenId)

            (bottomSheetFragment as NewPasswordFragment).show(
                this.supportFragmentManager,
                (bottomSheetFragment as NewPasswordFragment).tag
            )
        }
        else{
            Toast.makeText(this, "no data", Toast.LENGTH_SHORT).show()
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
                        Picasso.get()
                            .load(MediaManager.get().url().generate(resultData["url"] as String))
                            .into(binding.mainBackdrop)

                        val appUserRealm = realm.where<UserRealm>().equalTo("user_id", user?.id).findFirst()

                        realm.executeTransaction { transactionRealm ->
                            appUserRealm?.image = resultData["url"] as String
                        }

                        Toast.makeText(baseContext, "success", Toast.LENGTH_SHORT).show()
                        placeholder.visibility = View.GONE
                    }
                    override fun onError(requestId: String, error: ErrorInfo) {
                        Toast.makeText(baseContext, error?.description, Toast.LENGTH_SHORT).show()
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
        var username = "default"
        runBlocking {
            val taskName = async {
                (t_service as TelegramService).getProfileName()
            }
            username = taskName.await()
        }

        if(username != ""){
            return UserData(username, "Нажмите, чтобы изменить телеграм аккаунт", UserDataType.telegramAccount)
        }
        else{
            return UserData("", "Нажмите, чтобы добавить телеграм аккаунт", UserDataType.telegramAccount)
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
            return UserData(number, "Нажмите, чтобы изменить номер телефона", UserDataType.phoneNumber)
        }
        else{
            return UserData("", "Нажмите, чтобы добавить номер телефона", UserDataType.phoneNumber)
        }

    }
}