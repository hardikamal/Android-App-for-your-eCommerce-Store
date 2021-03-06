package com.iqonic.store.activity

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.CountDownTimer
import android.view.*
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.iqonic.store.AppBaseActivity
import com.iqonic.store.R
import com.iqonic.store.adapter.BaseAdapter
import com.iqonic.store.adapter.ProductImageAdapter
import com.iqonic.store.models.*
import com.iqonic.store.utils.BroadcastReceiverExt
import com.iqonic.store.utils.Constants.AppBroadcasts.CART_COUNT_CHANGE
import com.iqonic.store.models.RequestModel
import com.iqonic.store.models.StoreProductModel
import com.iqonic.store.models.StoreUpSale
import com.iqonic.store.utils.Constants.KeyIntent.DATA
import com.iqonic.store.utils.Constants.KeyIntent.EXTERNAL_URL
import com.iqonic.store.utils.Constants.KeyIntent.PRODUCT_ID
import com.iqonic.store.utils.extensions.*
import kotlinx.android.synthetic.main.activity_product_detail_new.*
import kotlinx.android.synthetic.main.item_group.view.*
import kotlinx.android.synthetic.main.item_group.view.ivProduct
import kotlinx.android.synthetic.main.item_group.view.tvAdd
import kotlinx.android.synthetic.main.item_group.view.tvOriginalPrice
import kotlinx.android.synthetic.main.item_group.view.tvProductName
import kotlinx.android.synthetic.main.item_home_newest_product.view.*
import kotlinx.android.synthetic.main.menu_cart.view.*
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs


class ProductDetailActivityNew : AppBaseActivity() {
    private var mPId = 0
    private val mImages = ArrayList<String>()
    private lateinit var mMenuCart: View
    private var isAddedToCart: Boolean = false
    private var mIsInWishList = false
    private var mIsExternalProduct = false
    var i: Int = 0
    private var EXTERNALURL: String = ""
    private var mAttributeAdapter: BaseAdapter<String>? = null
    private var mYearAdapter: ArrayAdapter<String>? = null
    private var mQuantity: String = "1"
    var image: String = ""

    private val mProductAdapter = BaseAdapter<StoreUpSale>(
        R.layout.item_home_newest_product,
        onBind = { view, model, position ->
            setProductItem1(view, model, position)
        })

    private fun setProductItem1(
        view: View,
        model: StoreUpSale,
        position: Int,
        params: Boolean = false
    ) {
        if (!params) {
            view.ivProduct.layoutParams = productLayoutParams()
        } else {
            view.ivProduct.layoutParams = productLayoutParamsForDealOffer()
        }

        if (model.images!![0].src!!.isNotEmpty()) {
            view.ivProduct.loadImageFromUrl(model.images!![0].src!!)
            image = model.images!![0].src!!
        }

        val mName = model.name!!.split(",")

        view.tvProductName.text = mName[0]

        if (model.sale_price!!.isNotEmpty()) {
            view.tvSaleLabel.visibility = View.VISIBLE
            view.tvDiscountPrice.text = model.sale_price!!.currencyFormat()
            view.tvOriginalPrice.applyStrike()
            view.tvOriginalPrice.text = model.regular_price!!.currencyFormat()
            view.tvOriginalPrice.visibility = View.VISIBLE
        } else {
            view.tvSaleLabel.visibility = View.GONE
            view.tvOriginalPrice.visibility = View.VISIBLE
            if (model.regular_price!!.isEmpty()) {
                view.tvOriginalPrice.text = ""
                view.tvDiscountPrice.text = model.price!!.currencyFormat()
            } else {
                view.tvOriginalPrice.text = ""
                view.tvDiscountPrice.text = model.regular_price!!.currencyFormat()
            }
        }

        view.onClick {
            launchActivity<ProductDetailActivityNew> {
                putExtra(PRODUCT_ID, model.id)
            }

        }
        view.tvAdd.onClick {
            AddCart(view, model, position)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_product_detail_new)
        setToolbar(toolbar)

        if (intent?.extras?.get(DATA) == null && intent?.extras?.get(PRODUCT_ID) == null) {
            toast(R.string.error_something_went_wrong)
            finish()
            return
        }
        mPId = intent?.getIntExtra(PRODUCT_ID, 0)!!

        BroadcastReceiverExt(this) {
            onAction(CART_COUNT_CHANGE) {
                setCartCountFromPref()
            }

        }
        rvLike?.setHorizontalLayout()
        rvLike?.adapter = mProductAdapter

        getProductDetail()
        if (isLoggedIn()) {
            loadApis()
        }
        tvItemProductOriginalPrice.applyStrike()

        btnAddCard.onClick {
            if (isLoggedIn()) {
                if (mIsExternalProduct) {
                    launchActivity<WebViewExternalProductActivity> {
                        putExtra(EXTERNAL_URL, EXTERNALURL)
                    }
                } else {
                    if (isAddedToCart) removeCartItem() else addItemToCart()
                }

            } else launchActivity<SignInUpActivity> { }

        }

        llReviews.onClick {
            launchActivity<ReviewsActivity> {
                putExtra(PRODUCT_ID, intent?.getIntExtra(PRODUCT_ID, 0)!!)
            }
        }

        toolbar_layout.setCollapsedTitleTextAppearance(R.style.CollapsedAppBar)
        app_bar.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            if (abs(verticalOffset) - app_bar.totalScrollRange == 0) {
                toolbar_layout.title = tvName.text
            } else {
                toolbar_layout.title = ""
            }
        })

        ivFavourite.onClick { onFavouriteClick() }

    }

    private fun AddCart(view: View, model: StoreUpSale, position: Int) {
        if (isLoggedIn()) {
            val requestModel = RequestModel()
            requestModel.pro_id = model.id
            requestModel.quantity = 1
            addItemToCart(requestModel, onApiSuccess = {
            })
        } else launchActivity<SignInUpActivity> { }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_dashboard, menu)
        val menuWishItem: MenuItem = menu!!.findItem(R.id.action_cart)
        menuWishItem.isVisible = true
        mMenuCart = menuWishItem.actionView
        menuWishItem.actionView.onClick {
            when {
                isLoggedIn() -> launchActivity<MyCartActivity>()
                else -> launchActivity<SignInUpActivity> { }
            }
        }
        setCartCount()
        return super.onCreateOptionsMenu(menu)
    }

    private fun setCartCountFromPref() {
        if (isLoggedIn()) {
            val count = getCartCount()
            mMenuCart.tvNotificationCount?.text = count
            if (count.checkIsEmpty() || count == "0") {
                mMenuCart.tvNotificationCount?.hide()
            } else {
                mMenuCart.tvNotificationCount?.show()
            }
        }

    }

    private fun addItemToCart() {
        val requestModel = RequestModel()
        requestModel.pro_id = mPId
        requestModel.quantity = mQuantity.toInt()
        addItemToCart(requestModel, onApiSuccess = {
            btnAddCard.text = getString(R.string.lbl_remove_cart)
            isAddedToCart = true
            fetchAndStoreCartData()

        })
    }

    private fun addItemToCartGroupItem(id: Int) {
        val requestModel = RequestModel()
        requestModel.pro_id = id
        requestModel.quantity = mQuantity.toInt()
        addItemToCart(requestModel, onApiSuccess = {
            fetchAndStoreCartData()

        })

    }

    fun setCartCount() {
        val count = getCartCount()
        mMenuCart.tvNotificationCount.text = count
        if (count.checkIsEmpty() || count == "0") {
            mMenuCart.tvNotificationCount.hide()
        } else {
            mMenuCart.tvNotificationCount.show()
        }
    }

    private fun removeCartItem() {
        val requestModel = RequestModel()
        requestModel.pro_id = mPId
        removeCartItem(requestModel, onApiSuccess = {
            btnAddCard.text = getString(R.string.lbl_add_to_cart)
            isAddedToCart = false
            fetchAndStoreCartData()

        })
    }

    private fun loadApis() {
        if (isNetworkAvailable()) {
            fetchAndStoreCartData()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun getProductDetail() {
        scrollView.visibility = View.GONE
        if (isNetworkAvailable()) {
            showProgress(true)

            getRestApiImpl().productDetail(mPId, onApiSuccess = {
                showProgress(false)
                scrollView.visibility = View.VISIBLE

                viewVariableImage(it[0])

                tvItemProductOriginalPrice.applyStrike()
                /**
                 * Other Information
                 *
                 */
                tvName.text = it[0].name
                toolbar_layout.title = it[0].name
                tvItemProductRating.rating = it[0].averageRating!!.toFloat()
                tvTags.text = it[0].description?.getHtmlString().toString()

                /**
                 * check stock
                 */
                if (it[0].in_stock == true) {
                    btnOutOfStock.hide()
                    btnAddCard.show()
                } else {
                    btnOutOfStock.show()
                    btnAddCard.hide()
                }

                /**
                 * Additional information
                 *
                 */
                if (it[0].attributes != null) {
                    for (att in it[0].attributes!!) {
                        val vi =
                            applicationContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                        val v: View = vi.inflate(R.layout.view_attributes, null)
                        val textView =
                            v.findViewById<View>(R.id.txtAttName) as TextView
                        textView.setTextColor(resources.getColor(R.color.textColorSecondary))
                        textView.text = att.name.toString() + " : "

                        val sizeList = ArrayList<String>()
                        val sizes = att.options
                        sizes?.forEachIndexed { i, s ->
                            sizeList.add(s.trim())
                        }
                        mAttributeAdapter =
                            BaseAdapter(R.layout.item_attributes, onBind = { vv, item, position ->
                                if (item.isNotEmpty()) {
                                    val attSize =
                                        vv.findViewById<View>(R.id.tvSize) as TextView
                                    attSize.typeface = fontRegular()
                                    attSize.setTextColor(resources.getColor(R.color.textColorSecondary))
                                    if (sizeList.size - 1 == position) {
                                        attSize.text = item
                                    } else {
                                        attSize.text = "$item ,"
                                    }
                                }
                            })
                        mAttributeAdapter?.clearItems()
                        mAttributeAdapter?.addItems(sizeList)
                        val recycleView =
                            v.findViewById<View>(R.id.rvAttributeView) as RecyclerView
                        recycleView.setHorizontalLayout()
                        recycleView.adapter = mAttributeAdapter

                        llAttributeView.addView(
                            v,
                            0,
                            ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.FILL_PARENT,
                                ViewGroup.LayoutParams.FILL_PARENT
                            )
                        )
                    }
                }

                /**
                 *  Attribute Information
                 */
                if (it[0].type == "simple") {
                    if (it[0].attributes!!.isNotEmpty()) {
                        tvAvailability.text = it[0].attributes!![0].name.toString()
                    }
                    llAttribute.hide()
                    setPriceDetail(it[0])
                } else if (it[0].type == "variable") {
                    llAttribute.show()
                    if (it[0].attributes != null && it[0].attributes?.isNotEmpty()!!) {

                        val sizeList = ArrayList<String>()
                        val mVariationsList = ArrayList<Int>()

                        val mVariations = it[0].variations!!

                        it.forEachIndexed { i, details ->
                            if (i > 0) {
                                var option = ""
                                it[i].attributes!!.forEach { attr ->
                                    option = if (option.isNotBlank()) {
                                        option + " - " + attr.optionsString.toString()
                                    } else {
                                        attr.optionsString.toString()
                                    }
                                }
                                if (details.onSale) {
                                    option = "$option [Sale]"
                                }
                                sizeList.add(option)
                            }
                        }

                        mVariations.forEachIndexed { index, s ->
                            mVariationsList.add(s)
                        }
                        mYearAdapter = ArrayAdapter(this, R.layout.spinner_items, sizeList)
                        spAttribute.adapter = this.mYearAdapter

                        spAttribute.onItemSelectedListener = object :
                            AdapterView.OnItemSelectedListener {

                            override fun onItemSelected(
                                parent: AdapterView<*>, view: View,
                                position: Int, id: Long
                            ) {
                                it.forEach { its ->
                                    if (mVariationsList[position] == its.id) {
                                        /*if (its.images!!.isNotEmpty()) {
                                            viewVariableImage(its)
                                        } else {
                                            viewVariableImage(it[0])
                                        }*/
                                        setPriceDetail(its)
                                        tvAvailability.text = its.attributes!![0].name.toString()
                                        mYearAdapter!!.notifyDataSetChanged()
                                    }
                                }
                            }

                            override fun onNothingSelected(parent: AdapterView<*>) {
                            }
                        }

                    } else {
                        llAttribute.hide()
                    }
                } else if (it[0].type == "grouped") {
                    llAttribute.visibility = View.GONE
                    upcomingSale.visibility = View.GONE
                    groupItems.visibility = View.VISIBLE

                    extraProduct.setVerticalLayout()
                    extraProduct.adapter = mGroupCartAdapter

                    mGroupCartAdapter.clearItems()
                    it.forEachIndexed { i, details ->
                        if (i > 0) {
                            mGroupCartAdapter.addItem(details)
                        }
                    }
                    mGroupCartAdapter.notifyDataSetChanged()
                } else if (it[0].type == "external") {
                    llAttribute.hide()
                    setPriceDetail(it[0])
                    mIsExternalProduct = true
                    btnAddCard.show()
                    btnAddCard.text = it[0].buttonText
                    EXTERNALURL = it[0].externalUrl.toString()
                } else {
                    toast(R.string.invalid_product)
                    finish()
                }

                // Purchasable
                if (!it[0].purchasable) {
                    if (mIsExternalProduct) {
                        banner_container.show()
                    } else {
                        banner_container.hide()
                    }
                } else {
                    banner_container.show()
                }

                // Review
                if (it[0].reviewsAllowed == true) {
                    tvAllReviews.show()
                    llReviews.show()
                    tvAllReviews.onClick {
                        launchActivity<ReviewsActivity> {
                            putExtra(PRODUCT_ID, intent?.getIntExtra(PRODUCT_ID, 0)!!)
                        }
                    }
                } else {
                    llReviews.hide()
                    tvAllReviews.hide()
                }

                // like data
                if (it[0].upsellIds.isNullOrEmpty()) {
                    lbl_like.hide()
                    rvLike.hide()
                } else {
                    lbl_like.show()
                    rvLike.show()
                    mProductAdapter.addItems(it[0].upsell_id!!)
                }


                // check cart & wish list
                when {
                    it[0].is_added_cart -> {
                        if (mIsExternalProduct) {
                            btnAddCard.text = it[0].buttonText
                        } else {
                            isAddedToCart = true
                            btnAddCard.text = getString(R.string.lbl_remove_cart)
                        }
                    }
                    else -> {
                        if (mIsExternalProduct) {
                            btnAddCard.text = it[0].buttonText
                        } else {
                            isAddedToCart = false
                            btnAddCard.text = getString(R.string.lbl_add_to_cart)
                        }
                    }
                }
                when {
                    it[0].is_added_wishlist -> {
                        mIsInWishList = true
                        changeFavIcon(
                            R.drawable.ic_heart_fill,
                            R.color.colorPrimary
                        )
                    }
                    else -> {
                        mIsInWishList = false
                        changeFavIcon(R.drawable.ic_heart, R.color.textColorSecondary)
                    }
                }

            }, onApiError = {
                showProgress(false)
                snackBar(it)
            })

        }
    }

    private fun calculateDiscount(salePrice: String?, regularPrice: String?): Float {
        return (100f - (salePrice!!.toFloat() * 100f) / regularPrice!!.toFloat())
    }

    /**
     * Header Images
     *
     */

    private fun viewVariableImage(its: StoreProductModel) {
        mImages.clear()
        for (i in 0 until its.images!!.size) {
            its.images?.get(i)?.src?.let { it1 -> mImages.add(it1) }
        }
        val adapter1 = ProductImageAdapter(mImages)
        productViewPager.adapter = adapter1
        dots.attachViewPager(productViewPager)
        dots.setDotDrawable(R.drawable.bg_circle_primary, R.drawable.black_dot)

        adapter1.setListener(object : ProductImageAdapter.OnClickListener {
            override fun onClick(position: Int) {
                launchActivity<ZoomImageActivity> {
                    putExtra(DATA, its)
                }
            }

        })
    }

    @SuppressLint("SetTextI18n")
    private fun setPriceDetail(its: StoreProductModel) {
        mPId = its.id
        if (its.onSale) {
            tvPrice.text = its.price?.currencyFormat()
            tvSale.show()
            tvItemProductOriginalPrice.applyStrike()
            tvItemProductOriginalPrice.text =
                its.regularPrice?.currencyFormat()
            upcomingSale.visibility = View.GONE

            val discount =
                calculateDiscount(its.salePrice, its.regularPrice)
            if (discount > 0.0) {
                tvSaleDiscount.visibility = View.VISIBLE
                tvSaleDiscount.text =
                    String.format("%.2f", discount) + getString(R.string.lbl_off)
            }
            onSaleOffer(its)
        } else {
            tvSaleDiscount.visibility = View.INVISIBLE
            tvItemProductOriginalPrice.text = ""
            tvPrice.text = its.regularPrice?.currencyFormat()
            tvSale.hide()
            showUpComingSale(its)
            tvSaleOffer.visibility = View.GONE
        }
    }

    /**
     * Showing Special Price Label
     *
     */
    @SuppressLint("SimpleDateFormat")
    private fun onSaleOffer(its: StoreProductModel) {
        if (its.dateOnSaleFrom != "") {
            tvSaleOffer.visibility = View.VISIBLE
            val endTime = its.dateOnSaleTo.toString() + " 23:59:59"
            val dateFormat = SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss"
            )
            try {
                val endDate: Date = dateFormat.parse(endTime)
                val currentDate = Date()
                val different: Long = endDate.time - currentDate.time
                object : CountDownTimer(different, 1000) {
                    @SuppressLint("SetTextI18n")
                    override fun onTick(millisUntilFinished: Long) {
                        var differenta: Long = millisUntilFinished
                        val sec = (millisUntilFinished / 1000).toString()
                        val secondsInMilli: Long = 1000
                        val minutesInMilli = secondsInMilli * 60
                        val hoursInMilli = minutesInMilli * 60
                        val daysInMilli = hoursInMilli * 24

                        val elapsedDays: Long = differenta / daysInMilli
                        differenta %= daysInMilli

                        val elapsedHours: Long = differenta / hoursInMilli
                        differenta %= hoursInMilli

                        val elapsedMinutes: Long = differenta / minutesInMilli
                        differenta %= minutesInMilli

                        val elapsedSeconds: Long = differenta / secondsInMilli
                        if (elapsedDays > 0) {
                            tvSaleOffer.text =
                                getString(R.string.lbl_special_price_ends_in_less_then) + " " + elapsedDays + getString(
                                    R.string.lbl_d
                                ) + " " + elapsedHours + getString(R.string.lbl_h) + " " + elapsedMinutes + getString(
                                    R.string.lbl_m
                                ) + " " + elapsedSeconds + getString(R.string.lbl_s)
                        } else {
                            tvSaleOffer.text =
                                getString(R.string.lbl_special_price_ends_in_less_then) + " " + elapsedHours + getString(
                                    R.string.lbl_h
                                ) + " " + elapsedMinutes + getString(R.string.lbl_m) + " " + elapsedSeconds + getString(
                                    R.string.lbl_s
                                )
                        }
                    }

                    override fun onFinish() {
                        tvSaleOffer.visibility = View.GONE
                    }
                }.start()
            } catch (e: ParseException) {
                e.printStackTrace()
            }

        } else {
            tvSaleOffer.visibility = View.GONE
        }
    }

    /**
     * Show Upcoming sale details
     *
     */
    @SuppressLint("SetTextI18n")
    private fun showUpComingSale(its: StoreProductModel) {
        if (its.dateOnSaleFrom != "") {
            upcomingSale.visibility = View.VISIBLE
            tvUpcomingSale.text =
                getString(R.string.lbl_sale_start_from) + " " + its.dateOnSaleFrom + " " + getString(
                    R.string.lbl_to
                ) + " " + its.dateOnSaleTo + ". " + getString(R.string.lbl_ge_amazing_discounts_on_the_products)
        } else {
            upcomingSale.visibility = View.GONE
        }
    }

    private fun onFavouriteClick() {
        if (mIsInWishList) {
            changeFavIcon(
                R.drawable.ic_heart,
                R.color.textColorSecondary
            ); ivFavourite.isClickable = false

            val requestModel = RequestModel(); requestModel.pro_id =
                mPId
            removeFromWishList(requestModel) {
                ivFavourite.isClickable = true
                mIsInWishList = false
                if (it) changeFavIcon(
                    R.drawable.ic_heart,
                    R.color.textColorSecondary
                ) else changeFavIcon(
                    R.drawable.ic_heart_fill,
                    R.color.tomato
                )
            }
        } else {
            if (isLoggedIn()) {
                changeFavIcon(
                    R.drawable.ic_heart_fill,
                    R.color.tomato
                ); ivFavourite.isClickable = false

                val requestModel = RequestModel()
                requestModel.pro_id = mPId
                addToWishList(requestModel) {
                    ivFavourite.isClickable = true
                    mIsInWishList = true
                    if (it) changeFavIcon(
                        R.drawable.ic_heart_fill,
                        R.color.tomato
                    ) else changeFavIcon(R.drawable.ic_heart, R.color.textColorSecondary)
                }
            } else {
                launchActivity<SignInUpActivity>()
            }
        }
    }

    private fun changeFavIcon(
        drawable: Int,
        iconTint: Int = R.color.textColorSecondary
    ) {
        ivFavourite.setImageResource(drawable)
        ivFavourite.applyColorFilter(color(iconTint))
    }

    /**
     * Grouped Items DisplayM
     *
     */
    private val mGroupCartAdapter =
        BaseAdapter<StoreProductModel>(R.layout.item_group, onBind = { view, model, position ->
            view.tvProductName.text = model.name
            view.tvOriginalPrice.applyStrike()
            if (model.images!![0].src!!.isNotEmpty()) {
                view.ivProduct.loadImageFromUrl(model.images!![0].src!!)
            }
            if (model.onSale) {
                view.tvPrice.text = model.salePrice?.currencyFormat()
                view.tvOriginalPrice.applyStrike()
                view.tvOriginalPrice.text =
                    model.regularPrice?.currencyFormat()
            } else {
                view.tvOriginalPrice.text = ""
                if (model.regularPrice.equals("")) {
                    view.tvPrice.text = model.price?.currencyFormat()
                } else {
                    view.tvPrice.text = model.regularPrice?.currencyFormat()
                }
            }

            view.tvAdd.onClick {
                if (isLoggedIn()) {
                    addItemToCartGroupItem(model.id)
                } else launchActivity<SignInUpActivity> { }
            }
        })

}

