package com.mongodb.alliance

import android.app.ActionBar
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.subscribe
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mongodb.alliance.adapters.UserDataAdapter
import com.mongodb.alliance.di.TelegramServ
import com.mongodb.alliance.events.ChangeUserDataEvent
import com.mongodb.alliance.model.UserData
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.ui.telegram.CodeFragment
import com.mongodb.alliance.ui.telegram.PasswordFragment
import com.mongodb.alliance.ui.telegram.PhoneNumberFragment
import dagger.hilt.android.AndroidEntryPoint
import io.realm.Realm
import kotlinx.coroutines.*
import java.lang.reflect.Field
import java.lang.reflect.Method
import javax.inject.Inject
import kotlin.time.ExperimentalTime


@InternalCoroutinesApi
@ExperimentalTime
@AndroidEntryPoint
class ProfileActivity : AppCompatActivity(), GlobalBroker.Subscriber {

    @TelegramServ
    @Inject
    lateinit var t_service: Service
    private lateinit var recyclerView: RecyclerView
    //private lateinit var binding: ActivityProfileBinding
    private lateinit var realm: Realm
    private lateinit var adapter : UserDataAdapter
    private lateinit var customActionBarView : View
    private lateinit var rootLayout : CoordinatorLayout
    private var bottomSheetFragment: BottomSheetDialogFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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


        customActionBarView.findViewById<ImageButton>(R.id.actionBar_button_back).setOnClickListener {
            finish()
        }
        customActionBarView.findViewById<ImageButton>(R.id.actionBar_button_menu).setOnClickListener {
            rootLayout = findViewById(R.id.coordinator_profile)
            var anchor = rootLayout.findViewById<View>(R.id.anchor_profile)
            val wrapper = ContextThemeWrapper(this, R.style.MyPopupMenu)
            val popup = PopupMenu(wrapper, anchor, Gravity.END)
            //val popup = PopupMenu(baseContext, it)

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
            e.printStackTrace()
        }

            popup.menuInflater.inflate(R.menu.profile_menu, popup.menu)

            popup.show()
        }

        realm = Realm.getDefaultInstance()
        //binding = ActivityProfileBinding.inflate(layoutInflater)
        //val view = binding.root
        //setContentView(view)
        setContentView(R.layout.test_profile_layout)
        recyclerView = findViewById(R.id.user_data_rec_view)
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
}