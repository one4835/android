package com.howl.howlstagram_f16.navigation

import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getColor
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.howlstagram.R
import com.example.howlstagram.navigation.model.AlarmDTO
import com.example.howlstagram.navigation.model.ContectDTO
import com.example.howlstagram.navigation.model.FollowDTO
import com.example.howlstagram_f16.LoginActivity
import com.example.howlstagram_f16.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.howl.howlstagram.f16.navigation.DetailViewFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_user.view.*

class UserFragment : Fragment(){
    var fragmentview : View? = null
    var firestore : FirebaseFirestore? = null
    var uid : String? = null
    var auth : FirebaseAuth? = null
    var currentUserUid : String? = null
    companion object {
        var PICK_PROFILE_FROM_ALBUM = 10
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        fragmentview = LayoutInflater.from(activity).inflate(R.layout.fragment_user,container,false)
        uid = arguments?.getString("destinationUid")
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserUid = auth?.currentUser?.uid

        if(uid == currentUserUid) {
            fragmentview?.account_bin_follow_signout?.text = getString(R.string.signout)
            fragmentview?.account_bin_follow_signout?.setOnClickListener{
                activity?.finish()
                startActivity(Intent(activity, LoginActivity::class.java))
                auth?.signOut()
            }
        }else {
            fragmentview?.account_bin_follow_signout?.text = getString(R.string.follow)
            var mainactivity = (activity as MainActivity)
                mainactivity?.toolbar_username?.text = arguments?.getString("userId")
                mainactivity?.toolbar_btn_back?.setOnClickListener {
                    mainactivity.bottom_navigation.selectedItemId = R.id.action_home
                }
                mainactivity?.toolbar_title_image?.visibility = View.GONE
                mainactivity?.toolbar_username?.visibility = View.VISIBLE
                mainactivity?.toolbar_btn_back?.visibility = View.VISIBLE
            fragmentview?.account_bin_follow_signout?.setOnClickListener {
                requestFollow ()
            }
        }
        fragmentview?.account_recyclerview?.adapter = UserFragmentRecyclerViewAdapter()
        fragmentview?.account_recyclerview?.layoutManager = GridLayoutManager(activity!!,3)

        fragmentview?. account_iv_profile?.setOnClickListener{
            var photoPickerIntent = Intent{Intent.ACTION_PICK}
            photoPickerIntent.type = "image/*"
            activity?.startActivityForResult(photoPickerIntent,PICK_PROFILE_FROM_ALBUM)
        }
        getProfileImage()
        getFollowerAndFollowing()
        return fragmentview
    }
    fun getFollowerAndFollowing(){
        firestore?.collection("users")?.document(uid!!)?.addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
            if(documentSnapshot == null) return@addSnapshotListener
            var followDTO = documentSnapshot.toObject(FollowDTO::class.java)
            if(followDTO?.followingCount != null){
                fragmentview?.account_tv_following_count?.text = followDTO.followingCount?.toString()
            }
            if(followDTO?.followerCount != null){
                fragmentview?.account_tv_follow_count?.text = followDTO?.followerCount?.toString()
                if (followDTO?.followers?.containsKey(currentUserUid!!)){
                    fragmentview?.account_bin_follow_signout?.text = getString(R.string.follow_cancel)
                    fragmentview?.account_bin_follow_signout?.background?.setColorFilter(ContextCompat,getColor(activity!!,R.color.colorLightGray),PorterDuff.Mode.MULTIPLY)
                }else{
                    if(uid != currentUserUid){
                        fragmentview?.account_bin_follow_signout?.text = getString(R.string.follow)
                        fragmentview?.account_bin_follow_signout?.background?.colorFilter = null
                    }
                }
            }
        }
    }
    fun requestFollow() {
        var tsDocFollowing = firestore?.collection("users")?.document(currentUserUid!!)
        firestore?.runTransaction { transaction ->
            var followDTO = transaction.get(tsDocFollowing!!).toObject(FollowDTO::class.java)
            if (followDTO == null){
                followDTO = FollowDTO()
                followDTO!!.followingCount = 1
                followDTO!!.followers[uid!!] = true

                transaction.set(tsDocFollowing,followDTO)
                return@runTransaction
            }

            if(followDTO.followings.containsKey(uid)) {
                followDTO?.followingCount = followDTO?.followingCount -1
                followDTO?.followers?.remove(uid)
            }else{
                followDTO?.followingCount = followDTO?.followingCount +1
                followDTO?.followers?.[uid!!] = true
            }
            transaction.set(tsDocFollowing,followDTO)
            return@runTransaction
        }
        var tsDocFollower = firestore?.collection("users")?.document(uid!!)
        firestore?.runTransaction { transaction ->
            var followDTO = transaction.get(tsDocFollower!!).toObject(FollowDTO::class.java)
            if(followDTO == null){
                followDTO = FollowDTO()
                followDTO!!.followerCount = 1
                followDTO!!.followers[currentUserUid!!] = true
                followerAlarm((uid!!))
                transaction.set(tsDocFollower,followDTO!!)
                return@runTransaction
            }

            if(followDTO!!.followers.containsKey(currentUserUid)){
                followDTO!!.followerCount = followDTO!!.followerCount - 1
                followDTO!!.followers.remove(currentUserUid!!)
            }else {
                followDTO!!.followerCount = followDTO!!.followerCount + 1
                followDTO!!.followers[currentUserUid!!] = true
                followerAlarm(uid!!)
            }
            transaction.set(tsDocFollower,followDTO!!)
            return@runTransaction
        }

    }
    fun followerAlarm(destinationUid : String){
        var alarmDTO = AlarmDTO()
        alarmDTO.destinationUid = destinationUid
        alarmDTO.userId = auth?.currentUser?.email
        alarmDTO.uid = auth?.currentUser?.uid
        alarmDTO.kind = 2
        alarmDTO.timestamp = System.currentTimeMillis()
        FirebaseFirestore.getInstance().collection("alarms").document().set(alarmDTO)
    }
    fun getProfileImage() {
        firestore?.collection("profileImages")?.document(uid!!)?.addSnapshotListener { documentSnapshot, firebaseFirestoreException ->
            if(documentSnapshot == null)return@addSnapshotListener
            if(documentSnapshot.data != null) {
                var url = documentSnapshot?.data!!["image"]
                Glide.with(activity!!).load(url).apply(RequestOptions().circleCrop()).into(fragmentview?.account_iv_profile!!)
            }
        }
    }
    inner class UserFragmentRecyclerViewAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(){
        var contentDTOs : ArrayList<ContentDTO> = arrayListOf()
        init {
                firestore?.collection("images")?.whereEqualTo("uid", uid)?.addSnapshotListener { querySnapshot, firebaseFirestoreException -> }
                if (querySnapshot == null)return@addSnapshotListener

                for (snapshot in querySnapshot.documents) {
                    contentDTOs.add(snapshot.toObject(ContentDTO::class.java)!!)
                }
                fragmentview?.account_tv_post_count?.text = contentDTOs.size.toString()
                notifyDataSetChanged()
            }
        }
        override fun onCreateViewHolder(p0: ViewGroup, p1: Int): RecyclerView.ViewHolder {
            var width = resources.displayMetrics.widthPixels / 3

            var imageview = ImageView(p0.context)
            imageview.LayoutParams = LinearLayoutCompat.LayoutParams(width,width)
            return DetailViewFragment.DetailViewRecyclerViewAdapter.CustomViewHolder(imageview)

        }
    inner class CustomViewHolder(var imageView: ImageView) : RecyclerView.ViewHolder(imageView) {

    }

        override fun getItemCount(): Int {
           return contentDTO.size
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            var imageview = (p0 as CustomViewHolder).imageview
            Glide.with(p0.itemView.context).load(contentDTO[p1].imageUrl).apply(RequestOptions().centerCrop()).into(imageview)
        }
    }
}