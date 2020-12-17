package com.mongodb.alliance.adapters

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
import com.mongodb.alliance.services.telegram.ClientState
import com.mongodb.alliance.services.telegram.Service
import com.mongodb.alliance.services.telegram.TelegramService
import com.mongodb.alliance.ui.FolderActivity
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.realm.Realm
import io.realm.kotlin.where
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select
import org.greenrobot.eventbus.EventBus
import java.io.File
import java.lang.Exception
import java.util.*
import javax.inject.Inject
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime


@InternalCoroutinesApi
@ExperimentalTime

class FolderAdapter @Inject constructor(var data: MutableList<FolderRealm>, var state : ClientState,
        private val tService : Service) : GlobalBroker.Publisher, ItemTouchHelperAdapter,
    RecyclerSwipeAdapter<FolderAdapter.FolderViewHolder>(), CoroutineScope, Filterable {

    var selectedFolders : MutableList<FolderRealm> = ArrayList()
    var foldersFilterList : MutableList<FolderRealm> = ArrayList()

    var selectedToPast : FolderRealm? = null

    var isPaste : Boolean = false
    var isSearching : Boolean = false
    var context : FolderActivity? = null
    var previousPos : Int = -1

    private var job: Job = Job()
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job

    init {
        foldersFilterList = data
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
        val itemView: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.new_folder_view_layout, parent, false)
        return FolderViewHolder(itemView)
    }

    override fun getSwipeLayoutResourceId(position: Int): Int {
        return R.id.swipe_layout
    }

    inner class FolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        var cardView : CardView = view.findViewById(R.id.card_view)
        var swipeLayout : SwipeLayout = view.findViewById(R.id.swipe_layout)
        var itemLayout : LinearLayout = view.findViewById(R.id.item_layout)
        var bottomWrapper : LinearLayout = view.findViewById(R.id.bottom_wrapper)
        var checkLayout : ConstraintLayout = view.findViewById(R.id.check_layout)
        var checkButton : ImageView = view.findViewById(R.id.check_folder)
        var name: TextView = view.findViewById(R.id.folder_name)
        var data: FolderRealm? = null
        var isSelecting : Boolean = false
        var isSelectedPaste : Boolean = false
    }

    override fun onBindViewHolder(
        holder: FolderViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if(payloads.isNotEmpty()) {
            if (payloads[0] is Boolean){
                if(payloads[0] == true) {
                    holder.cardView.cardElevation = 0f
                    holder.isSelectedPaste = false
                }
                else{
                    holder.cardView.cardElevation = 10f
                    holder.isSelectedPaste = true
                }
            }
            if(payloads[0] is Int){
                if(payloads[0] == 0){
                    holder.checkButton.visibility = View.VISIBLE
                    holder.cardView.cardElevation = 10f
                    holder.isSelecting = true
                }
                else{
                    holder.checkButton.visibility = View.INVISIBLE
                    holder.cardView.cardElevation = 0f
                    holder.isSelecting = false
                }
            }
        }else {
            super.onBindViewHolder(holder, position, payloads)
            if(selectedFolders.find { it._id == holder.data?._id } != null) {
                holder.checkButton.visibility =
                    View.VISIBLE
            }
        }
    }

    @ExperimentalCoroutinesApi
    override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
        if (foldersFilterList[position].isValid) {
            val obj: FolderRealm? = getItem(position)
            holder.data = obj
            holder.name.text = obj?.name
            holder.swipeLayout.isRightSwipeEnabled = false

            val count = holder.data?.nestedCount
            if (count != null) {
                if(state == ClientState.waitParameters){
                    runBlocking {
                        (tService as TelegramService).setUpClient()
                        state = tService.returnClientState()
                    }
                }
                if(state == ClientState.ready) {
                    loadImages(holder, count)
                }
                else{
                    showPlaceholders(holder, count)
                }
            }

            if(!isPaste) {
                holder.swipeLayout.showMode = SwipeLayout.ShowMode.LayDown
                holder.swipeLayout.addDrag(SwipeLayout.DragEdge.Left, holder.bottomWrapper)

                if (holder.data != null) {
                    mItemManger.bindView(holder.itemView, position)
                }

                holder.swipeLayout.addSwipeListener(object : SwipeLayout.SwipeListener {
                    override fun onClose(layout: SwipeLayout?) {

                    }

                    override fun onUpdate(layout: SwipeLayout?, leftOffset: Int, topOffset: Int) {

                    }

                    override fun onStartOpen(layout: SwipeLayout?) {
                        mItemManger.closeAllExcept(layout)
                    }

                    override fun onOpen(layout: SwipeLayout?) {

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

            holder.checkLayout.setOnLongClickListener {
                if(!isPaste) {
                    if (!holder.isSelecting) {
                        EventBus.getDefault().post(SelectFolderEvent(true))
                        holder.isSelecting = true
                        holder.data?.let { it1 -> selectedFolders.add(it1) }
                        notifyItemChanged(holder.position, 0)
                        return@setOnLongClickListener true
                    } else {
                        return@setOnLongClickListener false
                    }
                }
                else{
                    return@setOnLongClickListener false
                }
            }

            holder.checkLayout.setOnClickListener {
                if(!isPaste) {
                    if (!holder.isSelecting) {
                        if (selectedFolders.size > 0) {
                            EventBus.getDefault().post(SelectFolderEvent(true))
                            holder.isSelecting = true
                            holder.data?.let { it1 -> selectedFolders.add(it1) }
                            notifyItemChanged(holder.position, 0)
                        }
                        else{
                            holder.itemLayout.performClick()
                        }

                    } else {
                        EventBus.getDefault().post(SelectFolderEvent(false))
                        holder.isSelecting = false
                        holder.data?.let { it1 -> selectedFolders.remove(it1) }
                        notifyItemChanged(holder.position, 1)
                    }
                }
            }

            holder.itemLayout.setOnClickListener {
                if(!isPaste) {
                    if (selectedFolders.size <= 0) {
                        publish(OpenFolderEvent(holder.data?._id.toString()))
                    }
                    else{
                        if(holder.isSelecting) {
                            EventBus.getDefault().post(SelectFolderEvent(false))
                            holder.isSelecting = false
                            holder.data?.let { it1 -> selectedFolders.remove(it1) }
                            notifyItemChanged(holder.position, 1)
                        }
                        else{
                            EventBus.getDefault().post(SelectFolderEvent(true))
                            holder.isSelecting = true
                            holder.data?.let { it1 -> selectedFolders.add(it1) }
                            notifyItemChanged(holder.position, 0)
                        }
                    }
                }
                else{
                    if(selectedToPast != holder.data) {
                        selectedToPast = holder.data
                        notifyItemChanged(holder.position, false)
                        if(previousPos != -1) {
                            notifyItemChanged(previousPos, true)
                        }
                        previousPos = holder.position
                        EventBus.getDefault().post(CancelPasteSelectionEvent(false))
                        EventBus.getDefault().post(holder.data?._id?.let { it1 ->
                            SelectFolderToMoveEvent(
                                it1
                            )
                        })
                    }
                    else{
                        selectedToPast = null
                        notifyItemChanged(holder.position, true)
                        EventBus.getDefault().post(SelectFolderToMoveEvent(null))
                    }
                }
            }

            holder.bottomWrapper.findViewById<ImageButton>(R.id.pin_folder).setOnClickListener {
                mItemManger.closeAllItems()
                if (holder.data != null) {
                    val isP = (holder.data as FolderRealm).isPinned
                    if (!isP) {
                        val temp = findPinned()
                        if (temp != null) {
                            EventBus.getDefault().post(FolderPinDenyEvent("", holder, holder.data))
                        } else {
                            holder.data?.let { it1 -> setPinned(it1) }
                            EventBus.getDefault().post(FolderPinEvent("", holder.data!!))
                        }
                    }
                }
            }

            holder.bottomWrapper.findViewById<ImageButton>(R.id.edit_folder).setOnClickListener {
                mItemManger.closeAllItems()
                EventBus.getDefault().post(holder.data?.let { it1 -> EditFolderEvent(it1) })
            }

            if(selectedFolders.size != 0){
                if(selectedFolders.contains(holder.data)){
                    holder.checkButton.visibility = View.VISIBLE
                    holder.cardView.cardElevation = 10f
                    holder.isSelecting = true
                }
                else{
                    holder.checkButton.visibility = View.INVISIBLE
                    holder.cardView.cardElevation = 0f
                    holder.isSelecting = false
                }
            }
            else{
                holder.checkButton.visibility = View.INVISIBLE
                holder.cardView.cardElevation = 0f
                holder.isSelecting = false
            }

            if(selectedToPast != null){
                if(selectedToPast != holder.data) {
                    holder.cardView.cardElevation = 0f
                }
                else{
                    holder.cardView.cardElevation = 10f
                }
            }
            else{
                if(isPaste) {
                    holder.cardView.cardElevation = 0f
                }
            }
        }

    }

    fun setCurrState(st : ClientState){
        state = st
    }

    fun folderPin(folder : FolderRealm){
        mItemManger.closeAllItems()
        val isP = folder.isPinned
        if (!isP) {
            val temp = findPinned()
            if (temp != null) {
                EventBus.getDefault().post(FolderPinDenyEvent("", null, folder))
            } else {
                setPinned(folder)
                EventBus.getDefault().post(FolderPinEvent("", folder))
            }
        }
    }

    fun swapItems(startPos:Int, endPos:Int){
        mItemManger.closeAllItems()

        if (startPos < endPos) {
            for (i in startPos until endPos) {
                Collections.swap(foldersFilterList, i, i + 1)
            }
        } else {
            for (i in startPos downTo endPos + 1) {
                Collections.swap(foldersFilterList, i, i - 1)
            }
        }

        notifyItemMoved(startPos, endPos)

    }

    private fun loadImages(holder: FolderViewHolder, count: Int){
        val chats = getFirstChatsNames(holder)

        val pictures = holder.itemLayout.findViewById<LinearLayout>(R.id.pictures_layout)

        val thirdNestedPlaceholder = holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder)
        val secondNestedPlaceholder = holder.itemLayout.findViewById<TextView>(R.id.second_nested_placeholder)
        val firstNestedPlaceholder = holder.itemLayout.findViewById<TextView>(R.id.first_nested_placeholder)

        val thirdNested = holder.itemLayout.findViewById<ImageView>(R.id.third_nested)
        val secondNested = holder.itemLayout.findViewById<ImageView>(R.id.second_nested)
        val firstNested = holder.itemLayout.findViewById<ImageView>(R.id.first_nested)

        val additional = holder.itemLayout.findViewById<TextView>(R.id.additional_count)

        when (count){
            0 -> {
                pictures.visibility = View.INVISIBLE
            }
            1 -> {
                pictures.visibility = View.VISIBLE
                thirdNestedPlaceholder.text = chats.values.elementAt(0)[0].toString()
                thirdNestedPlaceholder.visibility = View.VISIBLE
                secondNestedPlaceholder.visibility = View.INVISIBLE
                firstNestedPlaceholder.visibility = View.INVISIBLE
                thirdNested.visibility = View.INVISIBLE
                secondNested.visibility = View.INVISIBLE
                firstNested.visibility = View.INVISIBLE
                additional.visibility = View.INVISIBLE
                launch {
                    Picasso.get().load(
                        File(
                            (tService as TelegramService).returnImagePath(
                                chats.values.elementAt(
                                    0
                                )
                            )
                        )
                    ).into(thirdNested,
                        object : Callback {
                            override fun onSuccess() {
                                thirdNestedPlaceholder.visibility =
                                    View.INVISIBLE
                                thirdNested.visibility =
                                    View.VISIBLE
                            }

                            override fun onError(e: Exception?) {
                                thirdNestedPlaceholder.visibility =
                                    View.VISIBLE
                                thirdNested.visibility =
                                    View.INVISIBLE
                            }
                        })
                }
            }
            2 -> {
                pictures.visibility = View.VISIBLE
                thirdNestedPlaceholder.text = chats.values.elementAt(0)[0].toString()
                secondNestedPlaceholder.text = chats.values.elementAt(1)[0].toString()
                thirdNestedPlaceholder.visibility = View.VISIBLE
                secondNestedPlaceholder.visibility = View.VISIBLE
                firstNestedPlaceholder.visibility = View.INVISIBLE
                thirdNested.visibility = View.INVISIBLE
                secondNested.visibility = View.INVISIBLE
                firstNested.visibility = View.INVISIBLE
                additional.visibility = View.INVISIBLE
                launch {
                    Picasso.get().load(
                        File(
                            (tService as TelegramService).returnImagePath(
                                chats.values.elementAt(
                                    1
                                )
                            )
                        )
                    ).into(thirdNested,
                        object : Callback {
                            override fun onSuccess() {
                                thirdNestedPlaceholder.visibility =
                                    View.INVISIBLE
                                thirdNested.visibility =
                                    View.VISIBLE
                            }

                            override fun onError(e: Exception?) {
                                thirdNestedPlaceholder.visibility =
                                    View.VISIBLE
                                thirdNested.visibility =
                                    View.INVISIBLE
                            }
                        })

                    Picasso.get().load(
                        File(
                            tService.returnImagePath(
                                chats.values.elementAt(
                                    0
                                )
                            )
                        )
                    ).into(secondNested,
                        object : Callback {
                            override fun onSuccess() {
                                secondNestedPlaceholder.visibility =
                                    View.INVISIBLE
                                secondNested.visibility =
                                    View.VISIBLE
                            }

                            override fun onError(e: Exception?) {
                                secondNestedPlaceholder.visibility =
                                    View.VISIBLE
                                secondNested.visibility =
                                    View.INVISIBLE
                            }
                        })
                }
            }
            3 -> {
                pictures.visibility = View.VISIBLE
                thirdNestedPlaceholder.text = chats.values.elementAt(0)[0].toString()
                secondNestedPlaceholder.text = chats.values.elementAt(1)[0].toString()
                firstNestedPlaceholder.text = chats.values.elementAt(2)[0].toString()
                thirdNestedPlaceholder.visibility = View.VISIBLE
                secondNestedPlaceholder.visibility = View.VISIBLE
                firstNestedPlaceholder.visibility = View.VISIBLE
                thirdNested.visibility = View.INVISIBLE
                secondNested.visibility = View.INVISIBLE
                firstNested.visibility = View.INVISIBLE
                additional.visibility = View.INVISIBLE
                launch {
                    Picasso.get().load(
                        File(
                            (tService as TelegramService).returnImagePath(
                                chats.values.elementAt(
                                    2
                                )
                            )
                        )
                    ).into(thirdNested,
                        object : Callback {
                            override fun onSuccess() {
                                thirdNestedPlaceholder.visibility =
                                    View.INVISIBLE
                                thirdNested.visibility =
                                    View.VISIBLE
                            }

                            override fun onError(e: Exception?) {
                                thirdNestedPlaceholder.visibility =
                                    View.VISIBLE
                                thirdNested.visibility =
                                    View.INVISIBLE
                            }
                        })

                    Picasso.get().load(
                        File(
                            tService.returnImagePath(
                                chats.values.elementAt(
                                    1
                                )
                            )
                        )
                    ).into(secondNested,
                        object : Callback {
                            override fun onSuccess() {
                                secondNestedPlaceholder.visibility =
                                    View.INVISIBLE
                                secondNested.visibility =
                                    View.VISIBLE
                            }

                            override fun onError(e: Exception?) {
                                secondNestedPlaceholder.visibility =
                                    View.VISIBLE
                                secondNested.visibility =
                                    View.INVISIBLE
                            }
                        })

                    Picasso.get().load(
                        File(
                            tService.returnImagePath(
                                chats.values.elementAt(
                                    0
                                )
                            )
                        )
                    ).into(firstNested,
                        object : Callback {
                            override fun onSuccess() {
                                firstNestedPlaceholder.visibility =
                                    View.INVISIBLE
                                firstNested.visibility =
                                    View.VISIBLE
                            }

                            override fun onError(e: Exception?) {
                                firstNestedPlaceholder.visibility =
                                    View.VISIBLE
                                firstNested.visibility =
                                    View.INVISIBLE
                            }
                        })
                }

            }
            else -> {
                pictures.visibility = View.VISIBLE
                thirdNestedPlaceholder.text = chats.values.elementAt(0)[0].toString()
                secondNestedPlaceholder.text = chats.values.elementAt(1)[0].toString()
                firstNestedPlaceholder.text = chats.values.elementAt(2)[0].toString()
                thirdNestedPlaceholder.visibility = View.VISIBLE
                secondNestedPlaceholder.visibility = View.VISIBLE
                firstNestedPlaceholder.visibility = View.VISIBLE
                thirdNested.visibility = View.INVISIBLE
                secondNested.visibility = View.INVISIBLE
                firstNested.visibility = View.INVISIBLE
                additional.text = "+" + (count - 3).toString()
                additional.visibility = View.VISIBLE
                launch {
                    Picasso.get().load(
                        File(
                            (tService as TelegramService).returnImagePath(
                                chats.values.elementAt(
                                    2
                                )
                            )
                        )
                    ).into(thirdNested,
                        object : Callback {
                            override fun onSuccess() {
                                thirdNestedPlaceholder.visibility =
                                    View.INVISIBLE
                                thirdNested.visibility =
                                    View.VISIBLE
                            }

                            override fun onError(e: Exception?) {
                                thirdNestedPlaceholder.visibility =
                                    View.VISIBLE
                                thirdNested.visibility =
                                    View.INVISIBLE
                            }
                        })

                    Picasso.get().load(
                        File(
                            tService.returnImagePath(
                                chats.values.elementAt(
                                    1
                                )
                            )
                        )
                    ).into(holder.itemLayout.findViewById(R.id.second_nested),
                        object : Callback {
                            override fun onSuccess() {
                                secondNestedPlaceholder.visibility =
                                    View.INVISIBLE
                                secondNested.visibility =
                                    View.VISIBLE
                            }

                            override fun onError(e: Exception?) {
                                secondNestedPlaceholder.visibility =
                                    View.VISIBLE
                                secondNested.visibility =
                                    View.INVISIBLE
                            }
                        })

                    Picasso.get().load(
                        File(
                            tService.returnImagePath(
                                chats.values.elementAt(
                                    0
                                )
                            )
                        )
                    ).into(firstNested,
                        object : Callback {
                            override fun onSuccess() {
                                firstNestedPlaceholder.visibility =
                                    View.INVISIBLE
                                firstNested.visibility =
                                    View.VISIBLE
                            }

                            override fun onError(e: Exception?) {
                                firstNestedPlaceholder.visibility =
                                    View.VISIBLE
                                firstNested.visibility =
                                    View.INVISIBLE
                            }
                        })
                }
            }

        }
    }

    private fun showPlaceholders(holder: FolderViewHolder, count: Int){
        val chats = getFirstChatsNames(holder)

        val pictures = holder.itemLayout.findViewById<LinearLayout>(R.id.pictures_layout)

        val thirdNestedPlaceholder = holder.itemLayout.findViewById<TextView>(R.id.third_nested_placeholder)
        val secondNestedPlaceholder = holder.itemLayout.findViewById<TextView>(R.id.second_nested_placeholder)
        val firstNestedPlaceholder = holder.itemLayout.findViewById<TextView>(R.id.first_nested_placeholder)

        val thirdNested = holder.itemLayout.findViewById<ImageView>(R.id.third_nested)
        val secondNested = holder.itemLayout.findViewById<ImageView>(R.id.second_nested)
        val firstNested = holder.itemLayout.findViewById<ImageView>(R.id.first_nested)

        val additional = holder.itemLayout.findViewById<TextView>(R.id.additional_count)

        when(count){
            0 -> {
                pictures.visibility = View.INVISIBLE
            }
            1 -> {
                pictures.visibility = View.VISIBLE
                thirdNestedPlaceholder.text = chats.values.elementAt(0)[0].toString()
                thirdNestedPlaceholder.visibility = View.VISIBLE
                secondNestedPlaceholder.visibility = View.INVISIBLE
                firstNestedPlaceholder.visibility = View.INVISIBLE
                thirdNested.visibility = View.INVISIBLE
                secondNested.visibility = View.INVISIBLE
                firstNested.visibility = View.INVISIBLE
                additional.visibility = View.INVISIBLE
            }
            2 -> {
                pictures.visibility = View.VISIBLE
                thirdNestedPlaceholder.text = chats.values.elementAt(0)[0].toString()
                secondNestedPlaceholder.text = chats.values.elementAt(1)[0].toString()
                thirdNestedPlaceholder.visibility = View.VISIBLE
                thirdNested.visibility = View.INVISIBLE
                secondNestedPlaceholder.visibility = View.VISIBLE
                secondNested.visibility = View.INVISIBLE
                firstNestedPlaceholder.visibility = View.INVISIBLE
                firstNested.visibility = View.INVISIBLE
                additional.visibility = View.INVISIBLE
            }
            3 -> {
                pictures.visibility = View.VISIBLE
                thirdNestedPlaceholder.text = chats.values.elementAt(0)[0].toString()
                secondNestedPlaceholder.text = chats.values.elementAt(1)[0].toString()
                firstNestedPlaceholder.text = chats.values.elementAt(2)[0].toString()
                thirdNestedPlaceholder.visibility = View.VISIBLE
                secondNestedPlaceholder.visibility = View.VISIBLE
                firstNestedPlaceholder.visibility = View.VISIBLE
                thirdNested.visibility = View.INVISIBLE
                secondNested.visibility = View.INVISIBLE
                firstNested.visibility = View.INVISIBLE
                additional.visibility = View.INVISIBLE
            }
            else -> {
                pictures.visibility = View.VISIBLE
                thirdNestedPlaceholder.text = chats.values.elementAt(0)[0].toString()
                secondNestedPlaceholder.text = chats.values.elementAt(1)[0].toString()
                firstNestedPlaceholder.text = chats.values.elementAt(2)[0].toString()
                thirdNestedPlaceholder.visibility = View.VISIBLE
                secondNestedPlaceholder.visibility = View.VISIBLE
                firstNestedPlaceholder.visibility = View.VISIBLE
                thirdNested.visibility = View.INVISIBLE
                secondNested.visibility = View.INVISIBLE
                firstNested.visibility = View.INVISIBLE
                additional.text = "+" + (count - 3).toString()
                additional.visibility = View.VISIBLE
            }
        }
    }

    private fun getFirstChatsNames(holder: FolderViewHolder) : HashMap<String, String>{
        val bgRealm = Realm.getDefaultInstance()
        val firstChats : HashMap<String, String> = HashMap()
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

    private fun getItem(position: Int) : FolderRealm?{
        return foldersFilterList[position]
    }

    fun setDataList(folders: MutableList<FolderRealm>) {
        foldersFilterList = folders
        notifyDataSetChanged()
    }

    fun cancelPasteSelection(){
        selectedToPast = null
        notifyItemChanged(previousPos, true)
        previousPos = -1
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        mItemManger.closeAllItems()

        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(foldersFilterList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(foldersFilterList, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
        updateOrders()

        return true
    }

    private fun updateOrders(){
        val bgRealm = Realm.getDefaultInstance()
        for (i in 0 until foldersFilterList.size) {
            launch {
                val tempFolder = bgRealm.where<FolderRealm>().equalTo("_id", foldersFilterList[i]._id)
                    .findFirst() as FolderRealm
                if(tempFolder.order != i) {
                    bgRealm.executeTransaction {
                        tempFolder.order = i
                    }
                }
                else{
                    cancel()
                }
            }
        }
        bgRealm.close()
    }

    private fun setPinned(folder : FolderRealm) {
        val bgRealm = Realm.getDefaultInstance()

        runBlocking {
            val tempFolder = bgRealm.where<FolderRealm>().equalTo("_id", folder._id)
                .findFirst() as FolderRealm
            bgRealm.executeTransaction {
                tempFolder.isPinned = true
            }
        }

        bgRealm.close()
    }

    private fun findPinned() : FolderRealm?{
        val bgRealm = Realm.getDefaultInstance()

        val result = bgRealm.where<FolderRealm>().equalTo("isPinned", true)
                .findFirst()

        bgRealm.close()
        return result
    }

    override fun getItemCount(): Int {
        return foldersFilterList.count()
    }

    override fun getItemViewType(position: Int): Int {
        return 0
    }

    fun cancelSelection(){
        notifyDataSetChanged()
        selectedFolders.clear()
    }

    fun updateItems(){
        for(i in 0..foldersFilterList.size){
            notifyItemChanged(i)
        }

        notifyDataSetChanged()
    }

    fun deleteSelected(){
        val bgRealm = Realm.getDefaultInstance()

        for (folder in selectedFolders) {
            val position : Int = foldersFilterList.indexOf(folder)
            bgRealm.executeTransaction { realm ->
                val results = realm.where<FolderRealm>().equalTo("_id", folder._id).findFirst()
                results?.deleteFromRealm()
            }
            notifyItemRemoved(position)
            foldersFilterList.removeAt(position)
        }

        selectedFolders.clear()
        bgRealm.close()

    }

    fun insert(folder : FolderRealm){
        notifyItemInserted(foldersFilterList.size + 1)
        foldersFilterList.add(folder)
    }

    fun setPasteMode(flag : Boolean){
        isPaste = flag
        notifyDataSetChanged()
    }

    fun filterResults(text : String) {
        if (text.isEmpty()) {
            foldersFilterList = data
            isSearching = false
        } else {
            val resultList : MutableList<FolderRealm> = ArrayList()
            for (row in data) {
                if (row.name.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT))) {
                    resultList.add(row)
                }
            }
            foldersFilterList = resultList
            isSearching = true
        }
        setDataList(foldersFilterList)
    }

    override fun getFilter() : Filter {
        return NamesFilter(this)
    }

    private class NamesFilter(private val adapter: FolderAdapter) : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            return FilterResults()
        }

        override fun publishResults(
            constraint: CharSequence,
            results: FilterResults?
        ) {
            adapter.filterResults(constraint.toString())
        }
    }

}