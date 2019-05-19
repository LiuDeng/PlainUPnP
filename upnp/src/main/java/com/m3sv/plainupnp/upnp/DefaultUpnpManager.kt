package com.m3sv.plainupnp.upnp


import com.bumptech.glide.request.RequestOptions
import com.m3sv.plainupnp.data.upnp.*
import com.m3sv.plainupnp.upnp.didl.ClingAudioItem
import com.m3sv.plainupnp.upnp.didl.ClingImageItem
import com.m3sv.plainupnp.upnp.didl.ClingVideoItem
import com.m3sv.upnp.R
import io.reactivex.BackpressureStrategy
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import timber.log.Timber
import java.util.concurrent.TimeUnit


/**
 * First is uri to a file, second is a title and third is an artist
 */

class DefaultUpnpManager constructor(
        private val controller: UpnpServiceController,
        private val factory: Factory,
        private val upnpNavigator: UpnpNavigator,
        override val rendererDiscovery: RendererDiscoveryObservable,
        override val contentDirectoryDiscovery: ContentDirectoryDiscoveryObservable
) : UpnpManager, UpnpNavigator by upnpNavigator {

    private val rendererStateSubject = PublishSubject.create<RendererState>()

    override val upnpRendererState: Observable<RendererState> = rendererStateSubject

    private val renderedItemSubject = PublishSubject.create<RenderedItem>()

    override val renderedItem: Observable<RenderedItem> = renderedItemSubject

    var contentState: ContentState? = null
        private set

    override val content: Observable<ContentState> = upnpNavigator.state.doOnNext {
        contentState = it
    }

    override val currentContentDirectory: UpnpDevice?
        get() = controller.selectedContentDirectory

    private val launchLocallySubject: PublishSubject<LaunchLocally> = PublishSubject.create()

    override val launchLocally: Observable<LaunchLocally> = launchLocallySubject.toFlowable(BackpressureStrategy.LATEST).toObservable()

    private val selectedDirectory = PublishSubject.create<Directory>()

    override val selectedDirectoryObservable: Observable<Directory> = selectedDirectory.toFlowable(BackpressureStrategy.LATEST).toObservable()

    private var upnpRendererStateObservable: UpnpRendererStateObservable? = null

    private var rendererStateDisposable: Disposable? = null

    private var rendererCommand: RendererCommand? = null

    private var isLocal: Boolean = false

    private var next: Int = -1

    private var previous: Int = -1

    private val renderItem: Subject<RenderItem> = PublishSubject.create()

    init {
        renderItem
                .throttleFirst(250, TimeUnit.MILLISECONDS)
                .subscribe(::render, Timber::e)

    }

    override fun selectContentDirectory(contentDirectory: UpnpDevice?) {
        Timber.d("Selected content directory: ${contentDirectory?.displayString}")
        controller.selectedContentDirectory = contentDirectory
        navigateHome()
    }

    override fun selectRenderer(renderer: UpnpDevice?) {
        Timber.d("Selected renderer: ${renderer?.displayString}")

        if (renderer is LocalDevice) {
            isLocal = true
        } else {
            isLocal = false
            controller.selectedRenderer = renderer
        }
    }

    override fun renderItem(item: RenderItem) {
        renderItem.onNext(item)
    }

    private fun render(item: RenderItem) {
        rendererCommand?.run {
            commandStop()
            pause()
        }

        rendererStateDisposable?.dispose()

        updateUi(item)

        if (isLocal) {
            launchItemLocally(item)
            return
        }

        next = item.position + 1
        previous = item.position - 1

        upnpRendererStateObservable = factory.createRendererState()

        rendererStateDisposable = upnpRendererStateObservable?.map {
            val newRendererState = RendererState(
                    it.remainingDuration,
                    it.elapsedDuration,
                    it.progress,
                    it.title,
                    it.artist,
                    it.state
            )

            Timber.i("New renderer state: $newRendererState")
            newRendererState
        }?.subscribeBy(onNext = {
            rendererStateSubject.onNext(it)

            if (it.state == UpnpRendererState.State.STOP) {
                rendererCommand?.pause()
            }
        }, onError = Timber::e)

        rendererCommand = factory.createRendererCommand(upnpRendererStateObservable)
                ?.apply {
                    if (item.item !is ClingImageItem)
                        resume()
                    else
                        rendererStateSubject.onNext(RendererState(progress = 0, state = UpnpRendererState.State.STOP))
                    launchItem(item.item)
                }
    }

    private fun launchItemLocally(item: RenderItem) {
        item.item.uri?.let { uri ->
            val contentType = when (item.item) {
                is ClingAudioItem -> "audio/*"
                is ClingImageItem -> "image/*"
                is ClingVideoItem -> "video/*"
                else -> null
            }

            contentType?.let {
                launchLocallySubject.onNext(LaunchLocally(uri, it))
            }
        }
    }

    /**
     * Updates control sheet with latest launched item
     */
    private fun updateUi(toRender: RenderItem) {
        val requestOptions = when (toRender.item) {
            is ClingAudioItem -> RequestOptions().placeholder(R.drawable.ic_music_note)
            else -> RequestOptions()
        }

        renderedItemSubject.onNext(RenderedItem(toRender.item.uri, toRender.item.title, requestOptions))
    }

    override fun playNext() {
        contentState?.let {
            if (it is ContentState.Success
                    && next in 0 until it.content.size
                    && it.content[next].didlObject is DIDLItem) {
                renderItem(RenderItem(it.content[next].didlObject as DIDLItem, next))
            }
        }
    }

    override fun playPrevious() {
        contentState?.let {
            if (it is ContentState.Success
                    && previous in 0 until it.content.size
                    && it.content[previous].didlObject is DIDLItem) {
                renderItem(RenderItem(it.content[previous].didlObject as DIDLItem, previous))
            }
        }
    }

    override fun resumeRendererUpdate() {
        rendererCommand?.resume()
    }

    override fun pauseRendererUpdate() {
        rendererCommand?.pause()
    }

    override fun pausePlayback() {
        rendererCommand?.commandPause()
    }

    override fun stopPlayback() {
        rendererCommand?.commandStop()
    }

    override fun resumePlayback() {
        rendererCommand?.commandPlay()
    }

    override fun browseHome() {
        navigateHome()
    }

    override fun browseTo(model: BrowseToModel) {
        navigateTo(model)
    }

    override fun browsePrevious(): Boolean = upnpNavigator.navigatePrevious()

    override fun moveTo(progress: Int, max: Int) {
        upnpRendererStateObservable?.run {
            rendererCommand?.run {
                formatTime(max, progress, durationSeconds)?.let {
                    Timber.d("Seek to $it")
                    commandSeek(it)
                }
            }
        }
    }

    override fun resumeUpnpController() {
        Timber.d("Resume UPnP controller")
        controller.resume()
    }

    override fun pauseUpnpController() {
        Timber.d("Pause UPnP upnpServiceController")
        controller.pause()
    }
}