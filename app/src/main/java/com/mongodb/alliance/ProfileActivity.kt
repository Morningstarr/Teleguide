package com.mongodb.alliance

import android.Manifest.permission.*
import android.annotation.TargetApi
import android.app.ActionBar
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import com.asksira.bsimagepicker.BSImagePicker
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.kroegerama.imgpicker.BottomSheetImagePicker
import com.kroegerama.imgpicker.ButtonType
import com.mongodb.alliance.adapters.UserDataAdapter
import com.mongodb.alliance.databinding.ActivityProfileBinding
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.ChangeUserDataEvent
import com.mongodb.alliance.model.UserData
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import com.mongodb.client.gridfs.GridFSBucket
import com.mongodb.client.gridfs.GridFSBuckets
import com.mongodb.client.gridfs.model.GridFSUploadOptions
import com.mongodb.stitch.android.core.Stitch
import com.mongodb.stitch.android.services.mongodb.remote.RemoteMongoClient
import com.mongodb.stitch.core.StitchClientConfiguration
import com.squareup.picasso.Picasso
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.Credentials
import io.realm.mongodb.User
import io.realm.mongodb.functions.Functions
import io.realm.mongodb.mongo.MongoClient
import io.realm.mongodb.mongo.MongoCollection
import kotlinx.coroutines.*
import org.bson.Document
import org.bson.types.ObjectId
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.net.URI
import javax.inject.Inject
import kotlin.time.ExperimentalTime


@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class ProfileActivity : AppCompatActivity(), GlobalBroker.Subscriber,
    BSImagePicker.OnSingleImageSelectedListener,
    BSImagePicker.OnMultiImageSelectedListener,
    BSImagePicker.ImageLoaderDelegate,
    BSImagePicker.OnSelectImageCancelledListener, BottomSheetImagePicker.OnImagesSelectedListener {

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
    private lateinit var chooseFragment : BottomSheetDialogFragment

    private var permissionsToRequest: ArrayList<String>? = null
    private val permissionsRejected: ArrayList<String> = ArrayList()
    private val permissions: ArrayList<String> = ArrayList()

    private val ALL_PERMISSIONS_RESULT = 107
    private val IMAGE_RESULT = 200

    private lateinit var realmApp: App
    private var mongoClient: MongoClient? = null
    private lateinit var mongoCollection: MongoCollection<Document>
    private lateinit var user: User
    private var TAG: String = "EXAMPLE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)



        //gridFS.openUploadStream("file uri")



        Picasso.get()
            .load("https://stitch-statichosting-prod.s3.amazonaws.com/5f2833ee066df9ccdb84ab8f/images/50.jpg")
            .into(binding.mainBackdrop)
            /*.takeIf {  binding.mainBackdrop.drawable == null }.apply { binding.profilePlaceholder.visibility = View.GONE  }*/



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
                    bottomSheetFragment =
                        NewPasswordFragment()

                    (bottomSheetFragment as NewPasswordFragment).show(
                        this.supportFragmentManager,
                        (bottomSheetFragment as NewPasswordFragment).tag
                    )
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
            //actionbar.setBackgroundDrawable(ColorDrawable(Color.parseColor("#FFFFFF")))
            //actionbar.setDisplayHomeAsUpEnabled(true)
            //actionbar.setDisplayHomeAsUpEnabled(true)
        }

        //rootLayout = binding.coordinatorProfile
        val fab = binding.chooseImageFab//rootLayout.findViewById<FloatingActionButton>(R.id.choose_image_fab)
        fab.setOnClickListener{
            /*val singleSelectionPicker: BSImagePicker =
                BSImagePicker.Builder("com.mongodb.alliance.fileprovider")
                    .setMaximumDisplayingImages(Integer.MAX_VALUE) //Default: Integer.MAX_VALUE. Don't worry about performance :)
                    .setSpanCount(3) //Default: 3. This is the number of columns
                    .setGridSpacing(Utils.dp2px(3)) //Default: 2dp. Remember to pass in a value in pixel.
                    .setPeekHeight(Utils.dp2px(360)) //Default: 360dp. This is the initial height of the dialog.
                    //.hideCameraTile() //Default: show. Set this if you don't want user to take photo.
                    //.hideGalleryTile() //Default: show. Set this if you don't want to further let user select from a gallery app. In such case, I suggest you to set maximum displaying images to Integer.MAX_VALUE.
                    .setTag("A request ID") //Default: null. Set this if you need to identify which picker is calling back your fragment / activity.

                    //.useFrontCamera() //Default: false. Launching camera by intent has no reliable way to open front camera so this does not always work.
                    .build()

            singleSelectionPicker.show(this.supportFragmentManager, "A request ID")*/

            val builder =
                BottomSheetImagePicker.Builder("com.mongodb.alliance.fileprovider")
                    .cameraButton(ButtonType.Tile)            //style of the camera link (Button in header, Image tile, None)
                    .galleryButton(ButtonType.Tile)           //style of the gallery link
                    .singleSelectTitle(R.string.pick_single)    //header text
                    .peekHeight(R.dimen.peekHeight)             //peek height of the bottom sheet
                    .columnSize(R.dimen.columnSize)             //size of the columns (will be changed a little to fit)
                    .requestTag("single")

            chooseFragment = builder.build()

            chooseFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.ImagePickerTheme)

            lifecycleScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (permissionsToRequest?.size!! > 0) {
                        permissionsToRequest?.toArray(Array<String>(permissionsToRequest!!.size) { i -> permissionsToRequest!![i] })
                            ?.let { requestPermissions(it, ALL_PERMISSIONS_RESULT) }
                    }
                    else{
                        chooseFragment.show(supportFragmentManager, "single")
                    }

                }
            }






                /*BottomSheetImagePicker.Builder("com.mongodb.alliance.fileprovider")
                    .cameraButton(ButtonType.Tile)            //style of the camera link (Button in header, Image tile, None)
                    .galleryButton(ButtonType.Tile)           //style of the gallery link
                    .singleSelectTitle(R.string.pick_single)    //header text
                    .peekHeight(R.dimen.peekHeight)             //peek height of the bottom sheet
                    .columnSize(R.dimen.columnSize)             //size of the columns (will be changed a little to fit)
                    .requestTag("single")
                    .show(supportFragmentManager)*/
            //}

        }

        customActionBarView.findViewById<ImageButton>(R.id.actionBar_button_back).setOnClickListener {
            finish()
        }
        customActionBarView.findViewById<ImageButton>(R.id.actionBar_button_menu).setOnClickListener {
            rootLayout = binding.coordinatorProfile //findViewById(R.id.coordinator_profile)
            var anchor = binding.anchorProfile //rootLayout.findViewById<View>(R.id.anchor_profile)
            val wrapper = ContextThemeWrapper(this, R.style.MyPopupMenu)
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

        realm = Realm.getDefaultInstance()
        val view = binding.root
        setContentView(view)
        //setContentView(R.layout.activity_profile)
        recyclerView = binding.userDataRecView//findViewById(R.id.user_data_rec_view)
        //setUpRecyclerView()

        //val currUser = realm.where<UserRealm>().equalTo("user_id", channelApp.currentUser()?.id).findFirst()
        //binding.profEmail.text = currUser?.name.toString()

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
        setUpRecyclerView()
        /*if(binding.mainBackdrop.drawable != null){
            binding.profilePlaceholder.visibility = View.VISIBLE
        }*/
    }

    private fun setUpRecyclerView() {
        var userDataArray = ArrayList<UserData>()
        userDataArray.add(UserData("kirill_kovrik@mail.ru","Нажмите, чтобы изменить почту", UserDataType.email))
        userDataArray.add(UserData("+380 71 331 2170", "Нажмите, чтобы изменить номер телефона", UserDataType.phoneNumber))
        userDataArray.add(UserData("YourMorningstar", "Нажмите, чтобы изменить телеграм аккаунт", UserDataType.telegramAccount))
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

    override fun onSingleImageSelected(uri: Uri?, tag: String?) {
        //TODO("Not yet implemented")
        if(uri != null){
            binding.mainBackdrop.setImageURI(uri)
        }
    }

    override fun onMultiImageSelected(uriList: MutableList<Uri>?, tag: String?) {
        //
    }

    override fun loadImage(imageUri: Uri?, ivImage: ImageView?) {
        //TODO("Not yet implemented")
    }

    override fun onCancelled(isMultiSelecting: Boolean, tag: String?) {
        //TODO("Not yet implemented")
    }

    override fun onImagesSelected(uris: List<Uri>, tag: String?) {
        try {

            val mongoClient =
                MongoClients.create("mongodb://kirill.kop.work%40gmail.com:" +
                        "11111111" + "@realm.mongodb.com:27020/?authMechanism=PLAIN&authSource=%24external&ssl=true" +
                        "&appName=prosumer-dwoso:teleguideDataCluster:local-userpass/")
            val database: MongoDatabase = mongoClient.getDatabase("prosumer")

            var gridFS: GridFSBucket? = null
            gridFS = GridFSBuckets.create(database, "images")
            val path = uris[0].path
            val streamToUploadFrom: InputStream = FileInputStream(FileUtils.getPath(baseContext, uris[0]))
            val options: GridFSUploadOptions = GridFSUploadOptions()
                .metadata(Document("type", "image"))

            val fileId: ObjectId =
                gridFS.uploadFromStream("loaded_image", streamToUploadFrom, options)
            funcsTest(uris[0])
        }
        catch(e:Exception){
            Timber.e(e.message)
        }
        //gridFS.uploadFromStream(uris[0])
        binding.mainBackdrop.setImageURI(uris[0])
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
                    chooseFragment.show(supportFragmentManager, "single")
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

    private fun funcsTest(uri : Uri){
        realm = Realm.getDefaultInstance()
        val functions = channelApp.getFunctions(channelApp.currentUser()!!)
        //functions.
        val credentials : Credentials = Credentials.anonymous()
        val client = Stitch.initializeDefaultAppClient("prosumer-dwoso")
        val service = client.getServiceClient("hosting")
        channelApp.loginAsync(credentials) {
            if (it.isSuccess) {
                user = channelApp.currentUser()!!
                mongoClient = user.getMongoClient("teleguideDataCluster")

                if (mongoClient != null) {
                    val database = mongoClient?.getDatabase("prosumer")
                    val collection = database?.getCollection("files")
                    /*var gridFS: GridFSBucket? = null
                    gridFS = GridFSBuckets.create(database as MongoDatabase, "images")
                    val path = uri.path
                    val streamToUploadFrom: InputStream = FileInputStream(FileUtils.getPath(baseContext, uri))
                    val options: GridFSUploadOptions = GridFSUploadOptions()
                        .metadata(Document("type", "image"))

                    val fileId: ObjectId =
                        gridFS.uploadFromStream("loaded_image", streamToUploadFrom, options)*/
                    //collection?.insertOne()
                    //collection?.insertOne(Document("field1", "value1"))
                } else {
                    Log.e(TAG, "Error connecting to the MongoDB instance.")
                }
            }
            else {
                Log.e(TAG, "Error logging into the Realm app. Make sure that anonymous authentication is enabled.")
            }
        }
    }

}