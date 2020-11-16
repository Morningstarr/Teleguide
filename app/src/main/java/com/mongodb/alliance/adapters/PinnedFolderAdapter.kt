package com.mongodb.alliance.adapters

import android.graphics.Color
import android.util.EventLog
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.broker.GlobalBroker
import cafe.adriel.broker.publish
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.mongodb.alliance.R
import com.mongodb.alliance.events.*
import com.mongodb.alliance.model.ChannelRealm
import com.mongodb.alliance.model.FolderRealm
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.ui.FolderActivity
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.lang.Exception
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime

@InternalCoroutinesApi
@ExperimentalTime
internal class PinnedFolderAdapter(var folder: FolderRealm) : GlobalBroker.Publisher,
    GlobalBroker.Subscriber, CoroutineScope,
    RecyclerSwipeAdapter<PinnedFolderAdapter.FolderViewHolder>(){

    private var isPaste : Boolean = false

    lateinit var t_service: Service

    private var selectedToPaste : FolderRealm? = null

    var context : FolderActivity? = null

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.new_folder_view_layout, parent, false)
        return FolderViewHolder(itemView)
    }

    override fun getSwipeLayoutResourceId(position: Int): Int {
        return R.id.swipe_layout
    }

    internal inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var swipeLayout : SwipeLayout = view.findViewById<SwipeLayout>(R.id.swipe_layout)
        var itemLayout : LinearLayout = view.findViewById<LinearLayout>(R.id.item_layout)
        var bottomWrapper : LinearLayout = view.findViewById<LinearLayout>(R.id.bottom_wrapper)
        var cardView : CardView = view.findViewById<CardView>(R.id.card_view)
        var name: TextView = view.findViewById(R.id.folder_name)
        var data: FolderRealm? = null
        var isPasteSelected : Boolean = false
        var additional: TextView = view.findViewById(R.id.additional_count)
        var checkLayout : ConstraintLayout = view.findViewById<ConstraintLayout>(R.id.check_layout)
        var isSelecting : Boolean = false
    }

    @ExperimentalCoroutinesApi
    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        holder.data = folder
        holder.name.text = folder.name
        holder.swipeLayout.isRightSwipeEnabled = false

        val count = holder.data?.nestedCount
        if(count != null){
            loadMiniatureImages(count, holder)
        }

        if(!isPaste) {
            holder.swipeLayout.showMode = SwipeLayout.ShowMode.LayDown

            holder.swipeLayout.addDrag(SwipeLayout.DragEdge.Left, holder.bottomWrapper)

            if (holder.data != null) {
                if ((holder.data as FolderRealm).isPinned) {
                    mItemManger.bindView(holder.itemView, position)
                    holder.itemLayout.findViewById<ImageView>(R.id.pinned).visibility = View.VISIBLE
                    val pinButton = holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_folder)
                    pinButton.setImageResource(R.drawable.ic_pin_blue_left)
                    pinButton.setBackgroundColor(Color.parseColor("#FFFFFF"))
                } else {
                    mItemManger.bindView(holder.itemView, position)
                }
            }
            holder.swipeLayout.addSwipeListener(object : SwipeLayout.SwipeListener {
                override fun onClose(layout: SwipeLayout?) {
                    //when the SurfaceView totally cover the BottomView.
                }

                override fun onUpdate(layout: SwipeLayout?, leftOffset: Int, topOffset: Int) {

                }

                override fun onStartOpen(layout: SwipeLayout?) {
                    mItemManger.closeAllExcept(layout)
                }

                override fun onOpen(layout: SwipeLayout?) {
                    //when the BottomView totally show.
                }

                override fun onStartClose(layout: SwipeLayout?) {

                }

                override fun onHandRelease(
                    layout: SwipeLayout?,
                    xvel: Float,
                    yvel: Float
                ) {

                }
            })
        }

        holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_folder).setOnClickListener {
            mItemManger.closeAllItems()
            if(holder.data != null) {
                holder.data?.let { it1 -> setPinned(it1, false) }
                EventBus.getDefault().post(holder.data?.let { it1 ->
                    FolderUnpinEvent("",
                        it1
                    )
                })
                holder.itemLayout.findViewById<ImageView>(R.id.pinned).visibility = View.INVISIBLE

                val pinButton = holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_folder)
                pinButton.setImageResource(R.drawable.ic_pin)
                pinButton.setBackgroundColor(Color.parseColor("#03CCFC"))
            }
        }

        holder.bottomWrapper.findViewById<ImageButton>(R.id.edit_folder).setOnClickListener{
            mItemManger.closeAllItems()
            EventBus.getDefault().post(holder.data?.let { it1 -> EditFolderEvent(it1) })
        }

        holder.itemLayout.setOnClickListener {
            if (!isPaste) {
                publish(OpenFolderEvent(holder.data?._id.toString()))
            } else {
                if (!holder.isPasteSelected) {
                    holder.cardView.cardElevation = 10f
                    holder.isPasteSelected = true
                    selectedToPaste = holder.data
                    EventBus.getDefault().post(CancelPasteSelectionEvent(true))
                    EventBus.getDefault().post(holder.data?._id?.let { it1 ->
                        SelectFolderToMoveEvent(
                            it1
                        )
                    })
                } else {
                    holder.cardView.cardElevation = 0f
                    holder.isPasteSelected = false
                    selectedToPaste = null
                    EventBus.getDefault().post(SelectFolderToMoveEvent(null))
                }
            }


        }

        holder.checkLayout.setOnClickListener {
            if(!isPaste) {
                if(context?.isSelecting!!) {
                    EventBus.getDefault().post(SelectPinnedFolderEvent())
                }
                else{
                    holder.itemLayout.performClick()
                }
            }
            else{
                holder.itemLayout.performClick()
            }
        }

        holder.checkLayout.setOnLongClickListener {
            if(!isPaste) {
                EventBus.getDefault().post(SelectPinnedFolderEvent())
            }
            return@setOnLongClickListener true
        }

        if(selectedToPaste == null){
            holder.isPasteSelected = false
            holder.cardView.cardElevation = 0f
        }

        holder.itemLayout.findViewById<ImageView>(R.id.pinned).visibility = View.VISIBLE
    }

    fun addContext(activity : FolderActivity){
        context = activity
    }

    fun cancelPasteSelection(){
        selectedToPaste = null
        notifyDataSetChanged()
    }

    fun findPinned() : FolderRealm?{
        val bgRealm = Realm.getDefaultInstance()

        val result = bgRealm.where<FolderRealm>().equalTo("isPinned", true)
            .findFirst()

        bgRealm.close()
        return result
    }

    fun setPasteMode(flag : Boolean){
        isPaste = flag
    }

    fun returnPasteMode() : Boolean {
        return isPaste
    }

    fun setPinned(folder : FolderRealm, pinned : Boolean) {
        val bgRealm = Realm.getDefaultInstance()

        runBlocking {
            val tempFolder = bgRealm.where<FolderRealm>().equalTo("_id", folder._id)
                .findFirst() as FolderRealm
            bgRealm.executeTransaction { realm ->
                tempFolder.isPinned = pinned
            }
        }

        bgRealm.close()
    }

    override fun getItemCount(): Int {
        return 1
    }

    @ExperimentalCoroutinesApi
    fun loadMiniatureImages(count : Int, holder : PinnedFolderAdapter.FolderViewHolder){
        val chats = getFirstChatsNames(holder)
        when (count){
            0 -> {
                holder.itemLayout.findViewById<LinearLayout>(R.id.pictures_layout).visibility = View.INVISIBLE
            }
            1 -> {
                holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder).text = chats.values.elementAt(0)[0].toString()
                holder.itemLayout.findViewById<TextView>(R.id.second_nested_placeholder).visibility = View.INVISIBLE
                holder.itemLayout.findViewById<TextView>(R.id.first_nested_placeholder).visibility = View.INVISIBLE
                launch {
                    val task = async {
                        Picasso.get().load(
                            File(
                                (t_service as TelegramService).downloadImageFile(chats.keys.elementAt(0))
                            )
                        ).into(holder.itemLayout.findViewById<ImageView>(R.id.third_nested),
                            object : Callback {
                                override fun onSuccess() {
                                    holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder).visibility =
                                        View.INVISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.third_nested).visibility =
                                        View.VISIBLE
                                }

                                override fun onError(e: Exception?) {
                                    holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder).visibility =
                                        View.VISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.third_nested).visibility =
                                        View.INVISIBLE
                                }
                            })
                    }
                    task.await()
                }
                holder.itemLayout.findViewById<ImageView>(R.id.second_nested).visibility = View.INVISIBLE
                holder.itemLayout.findViewById<ImageView>(R.id.first_nested).visibility = View.INVISIBLE
                holder.itemLayout.findViewById<TextView>(R.id.additional_count).visibility = View.INVISIBLE
            }
            2 -> {
                holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder).text = chats.values.elementAt(0)[0].toString()
                holder.itemLayout.findViewById<TextView>(R.id.second_nested_placeholder).text = chats.values.elementAt(1)[0].toString()
                holder.itemLayout.findViewById<TextView>(R.id.first_nested_placeholder).visibility = View.INVISIBLE
                launch {
                    val task = async {
                        Picasso.get().load(
                            File(
                                (t_service as TelegramService).downloadImageFile(chats.keys.elementAt(1))
                            )
                        ).into(holder.itemLayout.findViewById<ImageView>(R.id.third_nested),
                            object : Callback {
                                override fun onSuccess() {
                                    holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder).visibility =
                                        View.INVISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.third_nested).visibility =
                                        View.VISIBLE
                                }

                                override fun onError(e: Exception?) {
                                    holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder).visibility =
                                        View.VISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.third_nested).visibility =
                                        View.INVISIBLE
                                }
                            })

                        Picasso.get().load(
                            File(
                                (t_service as TelegramService).downloadImageFile(chats.keys.elementAt(0))
                            )
                        ).into(holder.itemLayout.findViewById<ImageView>(R.id.second_nested),
                            object : Callback {
                                override fun onSuccess() {
                                    holder.itemLayout.findViewById<TextView>(R.id.second_nested_placeholder).visibility =
                                        View.INVISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.second_nested).visibility =
                                        View.VISIBLE
                                }

                                override fun onError(e: Exception?) {
                                    holder.itemLayout.findViewById<TextView>(R.id.second_nested_placeholder).visibility =
                                        View.VISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.second_nested).visibility =
                                        View.INVISIBLE
                                }
                            })
                    }
                    task.await()
                }
                holder.itemLayout.findViewById<ImageView>(R.id.first_nested).visibility = View.INVISIBLE
                holder.itemLayout.findViewById<TextView>(R.id.additional_count).visibility = View.INVISIBLE
            }
            3 -> {
                holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder).text = chats.values.elementAt(0)[0].toString()
                holder.itemLayout.findViewById<TextView>(R.id.second_nested_placeholder).text = chats.values.elementAt(1)[0].toString()
                holder.itemLayout.findViewById<TextView>(R.id.first_nested_placeholder).text = chats.values.elementAt(2)[0].toString()
                launch {
                    val task = async {
                        Picasso.get().load(
                            File(
                                (t_service as TelegramService).downloadImageFile(chats.keys.elementAt(2))
                            )
                        ).into(holder.itemLayout.findViewById<ImageView>(R.id.third_nested),
                            object : Callback {
                                override fun onSuccess() {
                                    holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder).visibility =
                                        View.INVISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.third_nested).visibility =
                                        View.VISIBLE
                                }

                                override fun onError(e: Exception?) {
                                    holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder).visibility =
                                        View.VISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.third_nested).visibility =
                                        View.INVISIBLE
                                }
                            })

                        Picasso.get().load(
                            File(
                                (t_service as TelegramService).downloadImageFile(chats.keys.elementAt(1))
                            )
                        ).into(holder.itemLayout.findViewById<ImageView>(R.id.second_nested),
                            object : Callback {
                                override fun onSuccess() {
                                    holder.itemLayout.findViewById<TextView>(R.id.second_nested_placeholder).visibility =
                                        View.INVISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.second_nested).visibility =
                                        View.VISIBLE
                                }

                                override fun onError(e: Exception?) {
                                    holder.itemLayout.findViewById<TextView>(R.id.second_nested_placeholder).visibility =
                                        View.VISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.second_nested).visibility =
                                        View.INVISIBLE
                                }
                            })

                        Picasso.get().load(
                            File(
                                (t_service as TelegramService).downloadImageFile(chats.keys.elementAt(0))
                            )
                        ).into(holder.itemLayout.findViewById<ImageView>(R.id.first_nested),
                            object : Callback {
                                override fun onSuccess() {
                                    holder.itemLayout.findViewById<TextView>(R.id.first_nested_placeholder).visibility =
                                        View.INVISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.first_nested).visibility =
                                        View.VISIBLE
                                }

                                override fun onError(e: Exception?) {
                                    holder.itemLayout.findViewById<TextView>(R.id.first_nested_placeholder).visibility =
                                        View.VISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.first_nested).visibility =
                                        View.INVISIBLE
                                }
                            })
                    }
                    task.await()
                }

                holder.itemLayout.findViewById<TextView>(R.id.additional_count).visibility = View.INVISIBLE
            }
            else -> {
                holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder).text = chats.values.elementAt(0)[0].toString()
                holder.itemLayout.findViewById<TextView>(R.id.second_nested_placeholder).text = chats.values.elementAt(1)[0].toString()
                holder.itemLayout.findViewById<TextView>(R.id.first_nested_placeholder).text = chats.values.elementAt(2)[0].toString()
                launch {
                    val task = async {
                        Picasso.get().load(
                            File(
                                (t_service as TelegramService).downloadImageFile(chats.keys.elementAt(2))
                            )
                        ).into(holder.itemLayout.findViewById<ImageView>(R.id.third_nested),
                            object : Callback {
                                override fun onSuccess() {
                                    holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder).visibility =
                                        View.INVISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.third_nested).visibility =
                                        View.VISIBLE
                                }

                                override fun onError(e: Exception?) {
                                    holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder).visibility =
                                        View.VISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.third_nested).visibility =
                                        View.INVISIBLE
                                }
                            })

                        Picasso.get().load(
                            File(
                                (t_service as TelegramService).downloadImageFile(chats.keys.elementAt(1))
                            )
                        ).into(holder.itemLayout.findViewById<ImageView>(R.id.second_nested),
                            object : Callback {
                                override fun onSuccess() {
                                    holder.itemLayout.findViewById<TextView>(R.id.second_nested_placeholder).visibility =
                                        View.INVISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.second_nested).visibility =
                                        View.VISIBLE
                                }

                                override fun onError(e: Exception?) {
                                    holder.itemLayout.findViewById<TextView>(R.id.second_nested_placeholder).visibility =
                                        View.VISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.second_nested).visibility =
                                        View.INVISIBLE
                                }
                            })

                        Picasso.get().load(
                            File(
                                (t_service as TelegramService).downloadImageFile(chats.keys.elementAt(0))
                            )
                        ).into(holder.itemLayout.findViewById<ImageView>(R.id.first_nested),
                            object : Callback {
                                override fun onSuccess() {
                                    holder.itemLayout.findViewById<TextView>(R.id.first_nested_placeholder).visibility =
                                        View.INVISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.first_nested).visibility =
                                        View.VISIBLE
                                }

                                override fun onError(e: Exception?) {
                                    holder.itemLayout.findViewById<TextView>(R.id.first_nested_placeholder).visibility =
                                        View.VISIBLE
                                    holder.itemLayout.findViewById<ImageView>(R.id.first_nested).visibility =
                                        View.INVISIBLE
                                }
                            })
                    }
                    task.await()
                }

                holder.itemLayout.findViewById<TextView>(R.id.additional_count).text = "+" + (count - 3).toString()
            }
        }
    }

    fun initializeTService(t : TelegramService){
        t_service = t
    }

    private fun getFirstChatsNames(holder: PinnedFolderAdapter.FolderViewHolder) : HashMap<String, String>{
        val bgRealm = Realm.getDefaultInstance()
        val firstChats : HashMap<String, String> = HashMap<String, String>()
        bgRealm.executeTransaction { realm ->
            val firstChatsObjects = realm.where<ChannelRealm>().equalTo("folder._id", holder.data?._id).sort("order").limit(3).findAll().toMutableList()
            try {
                if (firstChatsObjects[0] != null) {
                    firstChats[firstChatsObjects[0].name] = firstChatsObjects[0].displayName
                }
                if (firstChatsObjects[1] != null) {
                    firstChats[firstChatsObjects[1].name] = firstChatsObjects[1].displayName
                }
                if (firstChatsObjects[2] != null) {
                    firstChats[firstChatsObjects[2].name] = firstChatsObjects[2].displayName
                }
            }
            catch(e:Exception){}
        }

        bgRealm.close()
        return firstChats
    }

}