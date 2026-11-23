// SPDX-License-Identifier: GPL-3.0-or-later
import QtQuick
import QtQuick.Window
import QtQuick.Shapes
import QtQuick.Dialogs
import app.nectarlink

// Dynamic Island-style floating pill anchored at the top-center of the primary
// monitor (340×40px compact, expanding smoothly to 380×88px on hover or incoming call).
// Stays hidden when idle and surfaces live activity:
//   1. Incoming / active phone call (caller name + Answer / Mute / On PC / Hang up)
//   2. File transfer in progress (circular progress ring + transfer title & speed)
//   3. Active media playback on Phone or PC (album art + scrolling song title + Play/Pause & Next)
Window {
    id: islandWin

    // GNOME on Wayland lets no app keep its own window on top. The "PiP on top"
    // extension does it for windows titled like this, for those who have it.
    title: onWayland ? qsTr("LinkHub Island") + " - PiP" : qsTr("LinkHub Island")
    // Sit directly in the top bar beside the calendar & clock without WM struts pushing it down.
    flags: Qt.Tool | Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint | Qt.WindowDoesNotAcceptFocus | Qt.BypassWindowManagerHint
    color: "transparent"

    readonly property int availW: Screen.desktopAvailableWidth > 0 ? Screen.desktopAvailableWidth : 1920

    // The work area (the screen minus the taskbar, wherever the taskbar is). Qt only knows
    // its size, so Windows reports the position; refreshed when the work area changes.
    property var workArea: AppController.workArea()
    readonly property int workAreaKey: Screen.desktopAvailableWidth * 100000 + Screen.desktopAvailableHeight
    onWorkAreaKeyChanged: workArea = AppController.workArea()
    readonly property real dpr: Screen.devicePixelRatio > 0 ? Screen.devicePixelRatio : 1
    readonly property real areaX: workArea && workArea.width ? workArea.x / dpr : Screen.virtualX
    readonly property real areaY: workArea && workArea.height ? workArea.y / dpr : Screen.virtualY
    readonly property real areaW: workArea && workArea.width ? workArea.width / dpr : availW

    // ---- Track active media across all rows of MediaList (Phone + This PC) ----
    property string mediaDeviceId: ""
    property string mediaPlayerId: ""
    property string mediaApp: ""
    property string mediaTitle: ""
    property string mediaArtist: ""
    property string mediaArt: ""
    property bool mediaPlaying: false
    property bool mediaCanPlay: true
    property bool mediaCanPause: true
    property bool mediaCanNext: true
    property bool mediaCanPrev: true
    property real mediaDuration: 0
    property real mediaPosition: 0
    // When mediaPosition was current, as JavaScript time.
    property real mediaPositionAt: 0
    // Phones only report the position when it jumps, so it's counted forward here.
    property real mediaNow: Date.now()
    readonly property real mediaPositionNow: mediaPosition
        + (mediaPlaying ? Math.max(0, mediaNow - mediaPositionAt) : 0)

    Timer {
        interval: 1000
        repeat: true
        running: islandWin.mediaPlaying && islandWin.mediaDuration > 0 && islandWin.mode === "media"
        onRunningChanged: if (running) islandWin.mediaNow = Date.now()
        onTriggered: islandWin.mediaNow = Date.now()
    }

    // ---- Track active file transfer ----
    property string activeTransferId: ""
    property string activeTransferTitle: ""
    // The last one's title, kept for "Transfer complete": its row can go
    // before the count of active transfers drops.
    property string finishedTransferTitle: ""
    property real activeTransferProgress: 0
    property real activeTransferRate: 0
    property bool activeTransferIncoming: false
    property bool transferJustFinished: false
    property bool transferFailed: false
    readonly property color transferEndColor: transferFailed ? "#F87171" : "#4ADE80"

    // ---- Transient notification toast (only when a new phone notification arrives) ----
    property bool notificationToastActive: false
    property int lastNotificationCount: NotificationList.count
    Timer {
        id: notificationToastTimer
        interval: 4200
        repeat: false
        onTriggered: islandWin.notificationToastActive = false
    }
    Connections {
        target: NotificationList
        function onCountChanged() {
            const cur = NotificationList.count
            if (cur > islandWin.lastNotificationCount && Preferences.nectarIslandNotifications
                && !AppController.notificationsPaused) {
                islandWin.notificationToastActive = true
                notificationToastTimer.restart()
            } else if (cur === 0) {
                islandWin.notificationToastActive = false
            }
            islandWin.lastNotificationCount = cur
        }
    }

    // QSG_RENDER_LOOP=basic advances QML's animation clock only while a window
    // renders; with the main window closed (App.qml) the Island is alone, so
    // its clock freezes. A wall-clock timer repaints each frame to re-advance
    // it, but only while something is really moving: the looping equalizer or
    // scrolling title, or a transition. A still pill is left alone.
    readonly property bool animating:
        (mode === "media" && mediaPlaying && !expanded && !Theme.reduceMotion)
        || compactTitleClip.shouldMarquee
        || transitionSettle.running

    // Holds `animating` true through the drop-in and expand/collapse Behaviors,
    // which would otherwise snap while the clock is frozen.
    Timer {
        id: transitionSettle
        interval: 320
        repeat: false
    }
    onExpandedChanged: if (!Theme.reduceMotion) transitionSettle.restart()
    onDropZoneOpenChanged: if (!Theme.reduceMotion) transitionSettle.restart()

    Timer {
        running: islandWin.visible && islandWin.animating
        interval: 16
        repeat: true
        onTriggered: islandWin.update()
    }

    Timer {
        id: transferDoneTimer
        interval: 3200
        repeat: false
        onTriggered: {
            islandWin.transferJustFinished = false
            islandWin.transferFailed = false
            islandWin.finishedTransferTitle = ""
        }
    }

    // Rows change before `active` does, so by the time this runs every transfer
    // that just ended has been through noteTransferEnded(). Nothing is shown when
    // they were all cancelled.
    property int lastActiveTransfers: TransferList.active
    onLastActiveTransfersChanged: {
        if (lastActiveTransfers > 0) {
            // A new transfer replaces a banner still showing, and starts afresh.
            if (transferJustFinished) {
                transferDoneTimer.stop()
                transferJustFinished = false
                transferFailed = false
                finishedTransferTitle = ""
            }
            return
        }
        if (finishedTransferTitle.length > 0) {
            transferJustFinished = true
            transferDoneTimer.restart()
        }
    }

    function noteTransferEnded(status, title) {
        if (status === "failed") {
            transferFailed = true
            finishedTransferTitle = title
        } else if (status === "done" && !transferFailed) {
            finishedTransferTitle = title
        }
    }

    function recomputeMedia() {
        let best = null
        const count = mediaRepeater.count
        for (let i = 0; i < count; i++) {
            const item = mediaRepeater.itemAt(i)
            if (!item) continue
            // Only surface phone media in Nectar Island (never PC-local media, which is already on the PC)
            if (item.deviceId === "local") continue
            if (item.playing) {
                if (!best || !best.playing) {
                    best = item
                }
            }
        }
        if (best) {
            mediaDeviceId = best.deviceId
            mediaPlayerId = best.playerId
            mediaApp = best.app
            mediaTitle = best.title
            mediaArtist = best.artist
            mediaArt = best.art
            mediaPlaying = best.playing
            mediaCanPlay = best.canPlay
            mediaCanPause = best.canPause
            mediaCanNext = best.canNext
            mediaCanPrev = best.canPrevious
            mediaDuration = best.duration
            mediaPosition = best.position
            mediaPositionAt = best.positionAt
            mediaNow = Date.now()
        } else {
            mediaDeviceId = ""
            mediaPlayerId = ""
            mediaApp = ""
            mediaTitle = ""
            mediaArtist = ""
            mediaArt = ""
            mediaPlaying = false
        }
    }

    function recomputeTransfer() {
        let found = null
        const count = transferRepeater.count
        for (let i = 0; i < count; i++) {
            const item = transferRepeater.itemAt(i)
            if (!item) continue
            if (item.status === "running" || item.status === "waiting" || item.status === "requested" || item.status === "paused") {
                found = item
                break
            }
        }
        if (found) {
            activeTransferId = found.transferId
            activeTransferTitle = found.title
            activeTransferProgress = Math.max(0, Math.min(1, found.progress || 0))
            activeTransferRate = found.rate || 0
            activeTransferIncoming = found.incoming
        } else if (!transferJustFinished) {
            activeTransferId = ""
            activeTransferTitle = ""
            activeTransferProgress = 0
            activeTransferRate = 0
        }
    }

    function formatRate(bytesPerSec) {
        const b = Number(bytesPerSec) || 0
        if (b <= 0) return ""
        if (b < 1024 * 1024)
            return Math.round(b / 1024) + " KB/s"
        return Qt.locale().toString(b / (1024 * 1024), "f", 1) + " MB/s"
    }

    readonly property bool hasMediaCandidate: mediaPlaying && mediaDeviceId !== "local" && mediaTitle.length > 0
    readonly property string mode: {
        if (Preferences.nectarIslandCalls && PhoneCall.active) return "call"
        if (Preferences.nectarIslandTransfers && (TransferList.active > 0 || transferJustFinished)) return "transfer"
        if (Preferences.nectarIslandMedia && hasMediaCandidate) return "media"
        if (Preferences.nectarIslandNotifications && notificationToastActive && NotificationList.count > 0) return "notification"
        if (Preferences.nectarIslandTransfers && TransferList.waitingCount > 0) return "waiting"
        return "idle"
    }

    // ---- Multi-device Drag & Drop state ----
    property string hoveredDropDeviceId: ""
    property string dropFeedbackText: ""
    property string quickPickDeviceId: ""
    property string quickPickDeviceName: ""
    readonly property bool dragActive: islandDropArea.containsDrag || hoveredDropDeviceId.length > 0
    readonly property bool dropZoneOpen: dragActive || dropFeedbackText.length > 0 || (mode === "idle" && pillHover.hovered)

    Timer {
        id: dropFeedbackTimer
        interval: 2600
        repeat: false
        onTriggered: islandWin.dropFeedbackText = ""
    }

    function sendDroppedToDevice(deviceId, deviceName, urls, text) {
        if (!deviceId || deviceId.length === 0) return
        if (urls && urls.length > 0) {
            TransferList.send(deviceId, urls)
            dropFeedbackText = qsTr("Mengirim %1 file ke %2…").arg(urls.length).arg(deviceName)
            dropFeedbackTimer.restart()
        } else if (text && text.trim().length > 0) {
            AppController.sendClipboard(deviceId)
            dropFeedbackText = qsTr("Dikirim ke %1").arg(deviceName)
            dropFeedbackTimer.restart()
        }
    }

    function extractDropUrls(drop) {
        const urls = []
        if (drop && drop.urls) {
            for (let i = 0; i < drop.urls.length; i++) {
                urls.push(drop.urls[i].toString())
            }
        }
        return urls
    }

    function defaultDropTarget() {
        if (DeviceList.count <= 0) return null
        return { id: DeviceList.deviceIdAt(0), name: DeviceList.deviceNameAt(0) }
    }

    FileDialog {
        id: islandFilePicker
        title: islandWin.quickPickDeviceName.length > 0
            ? qsTr("Kirim ke %1").arg(islandWin.quickPickDeviceName)
            : qsTr("Kirim file")
        fileMode: FileDialog.OpenFiles
        onAccepted: {
            if (islandWin.quickPickDeviceId.length > 0 && selectedFiles.length > 0) {
                const urls = selectedFiles.map(u => u.toString())
                islandWin.sendDroppedToDevice(islandWin.quickPickDeviceId, islandWin.quickPickDeviceName, urls, "")
            }
        }
    }

    readonly property bool hasActivity: mode !== "idle"
    readonly property bool onWayland: Qt.platform.pluginName.startsWith("wayland")
    property bool keptForMinutes: false
    onHasActivityChanged: {
        if (hasActivity) {
            keepTimer.stop()
            if (keptForMinutes && visible && !Theme.reduceMotion)
                dropInAnim.restart()
            keptForMinutes = onWayland
        } else if (keptForMinutes) {
            keepTimer.restart()
        }
    }
    Timer {
        id: keepTimer
        interval: 10 * 60 * 1000
        onTriggered: islandWin.keptForMinutes = false
    }
    // Always stay ready as a compact top-center Dynamic Island when enabled.
    visible: Preferences.nectarIsland

    onVisibleChanged: {
        if (!visible) {
            keepTimer.stop()
            keptForMinutes = false
        }
        if (visible) {
            Qt.callLater(() => AppController.styleShelfWindow())
            if (!Theme.reduceMotion) {
                dropInAnim.restart()
                transitionSettle.restart()
            }
        }
    }

    Component.onCompleted: {
        Qt.callLater(() => {
            recomputeMedia()
            recomputeTransfer()
            AppController.styleShelfWindow()
        })
    }

    Connections {
        target: Preferences
        function onNectarIslandChanged() {
            if (Preferences.nectarIsland) {
                Qt.callLater(() => AppController.styleShelfWindow())
            }
        }
    }

    readonly property bool expanded: !dropZoneOpen && (pillHover.hovered || (Preferences.nectarIslandCalls && PhoneCall.active && PhoneCall.ringing))
    readonly property real pillWidth: dropZoneOpen ? 516 : (expanded ? 380 : (mode === "idle" ? 204 : 310))
    readonly property real pillHeight: dropZoneOpen ? 132 : (expanded ? 88 : (mode === "idle" ? 26 : 30))
    width: Math.ceil(pillWidth + 12)
    height: Math.ceil(pillHeight + 8)
    // Placed at the very top bar right next to the GNOME center calendar & clock ("Min 22 Nov 22:24").
    x: areaX + Math.max(8, Math.min(areaW - width - 12, Math.round(areaW / 2 + 96)))
    y: 1

    readonly property string primaryLabel: {
        if (mode === "call")
            return PhoneCall.caller.length > 0 ? PhoneCall.caller : qsTr("Phone call")
        if (mode === "transfer") {
            if (transferJustFinished && TransferList.active === 0)
                return transferFailed ? qsTr("Transfer failed") : qsTr("Transfer complete")
            return activeTransferTitle.length > 0
                ? activeTransferTitle
                : qsTr("Transferring %1 file(s)…").arg(TransferList.active)
        }
        if (mode === "media")
            return mediaTitle.length > 0 ? mediaTitle : (mediaApp.length > 0 ? mediaApp : qsTr("Now playing"))
        if (mode === "notification")
            return qsTr("%1 phone notification(s)").arg(NotificationList.count)
        if (mode === "waiting")
            return qsTr("%1 waiting").arg(TransferList.waitingCount)
        return DeviceList.count > 0
            ? qsTr("LinkHub · %1 perangkat").arg(DeviceList.count)
            : qsTr("LinkHub Island · Siap")
    }

    // The call's clock, ticking once a second while a call is shown.
    property real callNow: Date.now()
    Timer {
        interval: 1000
        repeat: true
        running: islandWin.visible && islandWin.mode === "call" && PhoneCall.since > 0
        triggeredOnStart: true
        onTriggered: islandWin.callNow = Date.now()
    }
    readonly property string callClock: {
        if (PhoneCall.since <= 0) return ""
        const s = Math.max(0, Math.floor((callNow - PhoneCall.since) / 1000))
        const h = Math.floor(s / 3600)
        const m = Math.floor((s % 3600) / 60)
        const ss = String(s % 60).padStart(2, "0")
        return h > 0 ? h + ":" + String(m).padStart(2, "0") + ":" + ss : m + ":" + ss
    }

    readonly property string secondaryLabel: {
        if (mode === "call") {
            if (PhoneCall.ringing) return qsTr("Incoming call")
            // How long, where the sound is, and whether the other side hears this end.
            const parts = []
            if (callClock.length > 0) parts.push(callClock)
            if (PhoneCall.held) parts.push(qsTr("On hold"))
            if (PhoneCall.app.length > 0) parts.push(PhoneCall.app)
            parts.push(PhoneCall.callAudio ? qsTr("On PC") : qsTr("On phone"))
            if (PhoneCall.controls) parts.push(PhoneCall.muted ? qsTr("Mic off") : qsTr("Mic on"))
            return parts.join(" · ")
        }
        if (mode === "transfer") {
            if (transferJustFinished && TransferList.active === 0)
                return finishedTransferTitle
            const pct = Math.round(activeTransferProgress * 100) + "%"
            const spd = formatRate(activeTransferRate)
            const base = spd.length > 0 ? (pct + " · " + spd) : pct
            return TransferList.waitingCount > 0
                ? (base + " · " + qsTr("%1 waiting").arg(TransferList.waitingCount))
                : base
        }
        if (mode === "media") {
            const src = mediaDeviceId === "local" ? qsTr("This PC") : qsTr("Phone")
            const base = mediaArtist.length > 0
                ? (mediaArtist + " · " + src)
                : (mediaApp.length > 0 ? (mediaApp + " · " + src) : src)
            return TransferList.waitingCount > 0
                ? (base + " · " + qsTr("%1 waiting").arg(TransferList.waitingCount))
                : base
        }
        if (mode === "notification")
            return qsTr("Click to view in LinkHub")
        if (mode === "waiting") {
            return DeviceList.count > 0
                ? qsTr("Will send when %1 is back").arg(DeviceList.deviceNameAt(0))
                : qsTr("Will send when connected")
        }
        return qsTr("Live calls, transfers & media")
    }

    Item {
        id: root
        anchors.fill: parent
        visible: true

        // Hidden repeaters inside Item scope so QtQuick instantiates delegates cleanly
        Repeater {
            id: mediaRepeater
            model: Preferences.nectarIsland ? MediaList : null
            onItemAdded: islandWin.recomputeMedia()
            onItemRemoved: islandWin.recomputeMedia()
            delegate: Item {
                required property int index
                required property string deviceId
                required property string playerId
                required property string app
                required property string title
                required property string artist
                required property string art
                required property bool playing
                required property bool canPlay
                required property bool canPause
                required property bool canNext
                required property bool canPrevious
                required property real duration
                required property real position
                required property real positionAt
                visible: false
                onPlayingChanged: islandWin.recomputeMedia()
                onTitleChanged: islandWin.recomputeMedia()
                onArtistChanged: islandWin.recomputeMedia()
                onArtChanged: islandWin.recomputeMedia()
                onDeviceIdChanged: islandWin.recomputeMedia()
                onPositionChanged: islandWin.recomputeMedia()
                onDurationChanged: islandWin.recomputeMedia()
                onCanPlayChanged: islandWin.recomputeMedia()
                onCanPauseChanged: islandWin.recomputeMedia()
                onCanNextChanged: islandWin.recomputeMedia()
                onCanPreviousChanged: islandWin.recomputeMedia()
            }
        }

        Repeater {
            id: transferRepeater
            model: Preferences.nectarIsland ? TransferList : null
            onItemAdded: islandWin.recomputeTransfer()
            onItemRemoved: islandWin.recomputeTransfer()
            delegate: Item {
                required property int index
                required property string transferId
                required property string title
                required property real progress
                required property real rate
                required property bool incoming
                required property string status
                visible: false
                // Creating a row sets its status too, and an old row says nothing new.
                property bool created: false
                Component.onCompleted: created = true
                onStatusChanged: {
                    if (created)
                        islandWin.noteTransferEnded(status, title)
                    islandWin.recomputeTransfer()
                }
                onProgressChanged: islandWin.recomputeTransfer()
                onRateChanged: islandWin.recomputeTransfer()
                onTitleChanged: islandWin.recomputeTransfer()
            }
        }

        transform: Translate {
            id: dropSlide
            y: 0
        }

        NumberAnimation {
            id: dropInAnim
            target: dropSlide
            property: "y"
            from: -18
            to: 0
            duration: 240
            easing.type: Easing.OutBack
        }

        // Subtle outer ambient halo around the Dynamic Island capsule
        Rectangle {
            anchors.centerIn: pillBg
            width: pillBg.width + 6
            height: pillBg.height + 6
            radius: pillBg.radius + 3
            color: "transparent"
            border.width: islandWin.dragActive ? 2.5 : 1.5
            border.color: islandWin.dragActive
                ? Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.78)
                : (islandWin.mode === "call"
                    ? Qt.rgba(0.29, 0.87, 0.50, 0.32)
                    : Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.24))
        }

        Rectangle {
            id: pillBg
            anchors.top: parent.top
            anchors.topMargin: 4
            anchors.horizontalCenter: parent.horizontalCenter
            width: islandWin.pillWidth
            height: islandWin.pillHeight
            radius: islandWin.dropZoneOpen ? 26 : height / 2
            // Authentic deep Dynamic Island obsidian surface with crisp specular glass border
            color: Qt.rgba(0.05, 0.055, 0.075, Theme.glass ? 0.92 : 0.96)
            border.width: islandWin.dragActive ? 1.5 : 1
            border.color: islandWin.dragActive
                ? Theme.primary
                : (islandWin.mode === "call"
                    ? Qt.rgba(0.29, 0.87, 0.50, 0.55)
                    : Qt.rgba(1, 1, 1, 0.18))
            clip: true

            Behavior on width {
                enabled: !Theme.reduceMotion
                NumberAnimation { duration: 220; easing.type: Easing.OutBack }
            }
            Behavior on height {
                enabled: !Theme.reduceMotion
                NumberAnimation { duration: 220; easing.type: Easing.OutBack }
            }
            Behavior on radius {
                enabled: !Theme.reduceMotion
                NumberAnimation { duration: 200; easing.type: Easing.OutCubic }
            }

            // Whole-island DropArea: dragging any file near the top pill immediately opens
            // the multi-device drop zone; dropping outside a chip sends to the default device.
            DropArea {
                id: islandDropArea
                anchors.fill: parent
                onEntered: (drag) => {
                    drag.acceptProposedAction()
                }
                onDropped: (drop) => {
                    const urls = islandWin.extractDropUrls(drop)
                    const text = drop.text ? String(drop.text) : ""
                    const fallback = islandWin.defaultDropTarget()
                    if (fallback && (urls.length > 0 || text.trim().length > 0)) {
                        islandWin.sendDroppedToDevice(fallback.id, fallback.name, urls, text)
                    }
                    islandWin.hoveredDropDeviceId = ""
                    drop.acceptProposedAction()
                }
            }

            HoverHandler {
                id: pillHover
                cursorShape: Qt.PointingHandCursor
            }

            // Wayland ignores where we ask to put the window, so let it be dragged.
            DragHandler {
                target: null
                enabled: Qt.platform.pluginName === "wayland"
                onActiveChanged: if (active) islandWin.startSystemMove()
            }

            TapHandler {
                onTapped: (eventPoint) => {
                    if (islandWin.dropZoneOpen)
                        return
                    if (compactMediaControls.visible) {
                        const p1 = compactMediaControls.mapFromItem(pillBg, eventPoint.position)
                        if (p1.x >= 0 && p1.y >= 0 && p1.x <= compactMediaControls.width && p1.y <= compactMediaControls.height)
                            return
                    }
                    if (expandedControlsRow.visible) {
                        const p2 = expandedControlsRow.mapFromItem(pillBg, eventPoint.position)
                        if (p2.x >= 0 && p2.y >= 0 && p2.x <= expandedControlsRow.width && p2.y <= expandedControlsRow.height)
                            return
                    }
                    AppController.activateRequested()
                }
            }

            // ================================================================
            // COMPACT PILL VIEW (204×26px idle / 310×30px active)
            // ================================================================
            Item {
                id: compactView
                anchors.fill: parent
                anchors.leftMargin: 4
                anchors.rightMargin: 8
                visible: !islandWin.expanded && !islandWin.dropZoneOpen
                opacity: (!islandWin.expanded && !islandWin.dropZoneOpen) ? 1.0 : 0.0
                Behavior on opacity { NumberAnimation { duration: 140 } }

                // Left slot: Album Art / Progress Ring / Call Pulse / Brand Badge
                Item {
                    id: compactLeftSlot
                    width: islandWin.mode === "idle" ? 20 : 24
                    height: islandWin.mode === "idle" ? 20 : 24
                    anchors.left: parent.left
                    anchors.verticalCenter: parent.verticalCenter

                    // 1. Media album art squircle
                    Rectangle {
                        anchors.fill: parent
                        radius: 8
                        visible: islandWin.mode === "media"
                        color: Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.24)
                        Icon {
                            anchors.centerIn: parent
                            width: 14; height: 14
                            visible: !(islandWin.mediaArt.length > 0 && compactArtImg.status === Image.Ready)
                            path: Icons.music
                            color: Theme.primary
                        }
                        RoundedImage {
                            id: compactArtImg
                            anchors.fill: parent
                            radius: 8
                            source: islandWin.mediaArt
                            sourceSize: Qt.size(64, 64)
                        }
                    }

                    // 2. Live file transfer circular progress ring
                    Item {
                        anchors.fill: parent
                        visible: islandWin.mode === "transfer"

                        Shape {
                            anchors.fill: parent
                            preferredRendererType: Shape.CurveRenderer

                            ShapePath {
                                strokeColor: Qt.rgba(1, 1, 1, 0.16)
                                fillColor: "transparent"
                                strokeWidth: 2.6
                                capStyle: ShapePath.RoundCap
                                PathAngleArc {
                                    centerX: 13; centerY: 13
                                    radiusX: 10.5; radiusY: 10.5
                                    startAngle: 0; sweepAngle: 360
                                }
                            }
                            ShapePath {
                                strokeColor: islandWin.transferJustFinished ? islandWin.transferEndColor : Theme.primary
                                fillColor: "transparent"
                                strokeWidth: 2.6
                                capStyle: ShapePath.RoundCap
                                PathAngleArc {
                                    centerX: 13; centerY: 13
                                    radiusX: 10.5; radiusY: 10.5
                                    startAngle: -90
                                    sweepAngle: 360 * (islandWin.transferJustFinished ? 1.0 : Math.max(0.06, islandWin.activeTransferProgress))
                                }
                            }
                        }

                        Icon {
                            anchors.centerIn: parent
                            width: 11; height: 11
                            path: !islandWin.transferJustFinished ? Icons.send
                                : (islandWin.transferFailed ? Icons.close : Icons.check)
                            color: islandWin.transferJustFinished ? islandWin.transferEndColor : "#FFFFFF"
                        }
                    }

                    // 3. Call / Notification / Waiting / Idle brand badge
                    Rectangle {
                        anchors.fill: parent
                        radius: 13
                        visible: islandWin.mode === "call" || islandWin.mode === "notification" || islandWin.mode === "waiting" || islandWin.mode === "idle"
                        color: islandWin.mode === "call"
                            ? Qt.rgba(0.29, 0.87, 0.50, 0.22)
                            : Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.26)
                        Icon {
                            anchors.centerIn: parent
                            width: 14; height: 14
                            path: islandWin.mode === "call"
                                ? Icons.call
                                : (islandWin.mode === "notification"
                                    ? Icons.bell
                                    : (islandWin.mode === "waiting" ? Icons.send : Icons.mark))
                            color: islandWin.mode === "call" ? "#4ADE80" : "#60A5FA"
                        }
                    }
                }

                // Right slot: Equalizer + inline quick transport controls (or % badge)
                Row {
                    id: compactMediaControls
                    anchors.right: parent.right
                    anchors.verticalCenter: parent.verticalCenter
                    spacing: 4

                    // A call in progress: where its sound is, the microphone, and how long.
                    Row {
                        id: compactCallStatus
                        anchors.verticalCenter: parent.verticalCenter
                        spacing: 6
                        visible: islandWin.mode === "call" && !PhoneCall.ringing
                        Accessible.role: Accessible.StaticText
                        Accessible.name: islandWin.secondaryLabel
                        Icon {
                            anchors.verticalCenter: parent.verticalCenter
                            width: 12; height: 12
                            path: PhoneCall.callAudio ? Icons.desktop : Icons.phone
                            color: PhoneCall.callAudio ? "#4ADE80" : Qt.rgba(1, 1, 1, 0.7)
                        }
                        Icon {
                            visible: PhoneCall.controls
                            anchors.verticalCenter: parent.verticalCenter
                            width: 12; height: 12
                            path: PhoneCall.muted ? Icons.micOff : Icons.mic
                            color: PhoneCall.muted ? "#EF4444" : Qt.rgba(1, 1, 1, 0.7)
                        }
                        Txt {
                            visible: text.length > 0
                            anchors.verticalCenter: parent.verticalCenter
                            role: "label"
                            size: 12
                            color: PhoneCall.held ? "#FBBF24" : Qt.rgba(1, 1, 1, 0.85)
                            text: islandWin.callClock
                        }
                    }

                    // Animated 3-bar equalizer in media mode
                    Row {
                        anchors.verticalCenter: parent.verticalCenter
                        spacing: 1.8
                        height: 12
                        visible: islandWin.mode === "media"
                        Repeater {
                            model: 3
                            delegate: Rectangle {
                                id: cEqBar
                                required property int index
                                width: 2.2
                                radius: 1.1
                                anchors.bottom: parent.bottom
                                color: Theme.primary
                                height: islandWin.mediaPlaying ? 8 : 3
                                SequentialAnimation on height {
                                    running: islandWin.visible && !islandWin.expanded && islandWin.mediaPlaying && !Theme.reduceMotion
                                    loops: Animation.Infinite
                                    NumberAnimation {
                                        from: 3
                                        to: cEqBar.index === 1 ? 12 : (cEqBar.index === 0 ? 9 : 10)
                                        duration: 240 + cEqBar.index * 80
                                        easing.type: Easing.InOutSine
                                    }
                                    NumberAnimation {
                                        to: 3
                                        duration: 220 + cEqBar.index * 70
                                        easing.type: Easing.InOutSine
                                    }
                                }
                            }
                        }
                    }

                    // Quick Play/Pause button right in the compact pill
                    Rectangle {
                        visible: islandWin.mode === "media"
                        width: 24; height: 24
                        radius: 12
                        anchors.verticalCenter: parent.verticalCenter
                        color: cPlayHover.hovered ? Qt.rgba(1, 1, 1, 0.18) : Qt.rgba(1, 1, 1, 0.08)
                        Icon {
                            anchors.centerIn: parent
                            width: 12; height: 12
                            path: islandWin.mediaPlaying ? Icons.pause : Icons.play
                            color: "#FFFFFF"
                        }
                        HoverHandler { id: cPlayHover; cursorShape: Qt.PointingHandCursor }
                        TapHandler {
                            onTapped: MediaList.command(
                                islandWin.mediaDeviceId,
                                islandWin.mediaPlayerId,
                                islandWin.mediaPlaying ? "pause" : "play",
                                0
                            )
                        }
                    }

                    // Quick Next Track button right in the compact pill
                    Rectangle {
                        visible: islandWin.mode === "media" && islandWin.mediaCanNext
                        width: 24; height: 24
                        radius: 12
                        anchors.verticalCenter: parent.verticalCenter
                        color: cNextHover.hovered ? Qt.rgba(1, 1, 1, 0.18) : "transparent"
                        Icon {
                            anchors.centerIn: parent
                            width: 12; height: 12
                            path: Icons.skipNext
                            color: Qt.rgba(1, 1, 1, 0.85)
                        }
                        HoverHandler { id: cNextHover; cursorShape: Qt.PointingHandCursor }
                        TapHandler {
                            onTapped: MediaList.command(islandWin.mediaDeviceId, islandWin.mediaPlayerId, "next", 0)
                        }
                    }

                    // Transfer percentage pill
                    Txt {
                        visible: islandWin.mode === "transfer" && !islandWin.transferJustFinished
                        anchors.verticalCenter: parent.verticalCenter
                        role: "caption"
                        size: 11
                        weight: Font.Bold
                        color: Theme.primary
                        text: Math.round(islandWin.activeTransferProgress * 100) + "%"
                    }
                }

                // Center slot: Scrolling / crisp title + subtle artist/subtitle
                Item {
                    id: compactTitleClip
                    anchors.left: compactLeftSlot.right
                    anchors.leftMargin: 9
                    anchors.right: compactMediaControls.left
                    anchors.rightMargin: 8
                    anchors.verticalCenter: parent.verticalCenter
                    height: 24
                    clip: true

                    readonly property bool shouldMarquee: compactTitleMetrics.width > compactTitleClip.width
                        && islandWin.visible && !islandWin.expanded && !Theme.reduceMotion

                    TextMetrics {
                        id: compactTitleMetrics
                        font: compactTitleTxt.font
                        text: islandWin.primaryLabel + (islandWin.mediaArtist.length > 0 && islandWin.mode === "media" ? "  ·  " + islandWin.mediaArtist : "")
                    }

                    Txt {
                        id: compactTitleTxt
                        anchors.verticalCenter: parent.verticalCenter
                        width: compactTitleClip.shouldMarquee ? compactTitleMetrics.width : compactTitleClip.width
                        role: "label"
                        size: 12
                        weight: Font.DemiBold
                        color: "#FFFFFF"
                        elide: compactTitleClip.shouldMarquee ? Text.ElideNone : Text.ElideRight
                        text: compactTitleMetrics.text

                        SequentialAnimation on x {
                            running: compactTitleClip.shouldMarquee && islandWin.visible && !islandWin.expanded
                                     && !Theme.reduceMotion
                            onRunningChanged: if (!running) compactTitleTxt.x = 0
                            loops: Animation.Infinite
                            PauseAnimation { duration: 1200 }
                            NumberAnimation {
                                from: 0
                                to: -Math.max(0, compactTitleMetrics.width - compactTitleClip.width + 12)
                                duration: Math.max(1800, (compactTitleMetrics.width - compactTitleClip.width) * 28)
                                easing.type: Easing.InOutSine
                            }
                            PauseAnimation { duration: 900 }
                            NumberAnimation {
                                to: 0
                                duration: 900
                                easing.type: Easing.OutCubic
                            }
                        }
                        onTextChanged: x = 0
                    }
                }
            }

            // ================================================================
            // EXPANDED PILL VIEW (380×88px on hover or incoming call)
            // ================================================================
            Item {
                id: expandedView
                anchors.fill: parent
                anchors.leftMargin: 14
                anchors.rightMargin: 14
                anchors.topMargin: 12
                anchors.bottomMargin: 12
                visible: islandWin.expanded
                opacity: islandWin.expanded ? 1.0 : 0.0
                Behavior on opacity { NumberAnimation { duration: 160 } }

                // Left cover / artwork / progress dial (48×48px)
                Item {
                    id: expLeftSlot
                    width: 48
                    height: 48
                    anchors.left: parent.left
                    anchors.verticalCenter: parent.verticalCenter

                    Rectangle {
                        anchors.fill: parent
                        radius: 12
                        visible: islandWin.mode === "media"
                        color: Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.24)
                        Icon {
                            anchors.centerIn: parent
                            width: 22; height: 22
                            visible: !(islandWin.mediaArt.length > 0 && expArtImg.status === Image.Ready)
                            path: Icons.music
                            color: Theme.primary
                        }
                        RoundedImage {
                            id: expArtImg
                            anchors.fill: parent
                            radius: 12
                            source: islandWin.mediaArt
                            sourceSize: Qt.size(112, 112)
                        }
                    }

                    Item {
                        anchors.fill: parent
                        visible: islandWin.mode === "transfer"

                        Shape {
                            anchors.fill: parent
                            preferredRendererType: Shape.CurveRenderer
                            ShapePath {
                                strokeColor: Qt.rgba(1, 1, 1, 0.16)
                                fillColor: "transparent"
                                strokeWidth: 3.4
                                capStyle: ShapePath.RoundCap
                                PathAngleArc {
                                    centerX: 24; centerY: 24
                                    radiusX: 19; radiusY: 19
                                    startAngle: 0; sweepAngle: 360
                                }
                            }
                            ShapePath {
                                strokeColor: islandWin.transferJustFinished ? islandWin.transferEndColor : Theme.primary
                                fillColor: "transparent"
                                strokeWidth: 3.4
                                capStyle: ShapePath.RoundCap
                                PathAngleArc {
                                    centerX: 24; centerY: 24
                                    radiusX: 19; radiusY: 19
                                    startAngle: -90
                                    sweepAngle: 360 * (islandWin.transferJustFinished ? 1.0 : Math.max(0.06, islandWin.activeTransferProgress))
                                }
                            }
                        }

                        Txt {
                            anchors.centerIn: parent
                            size: 10
                            weight: Font.Bold
                            color: "#FFFFFF"
                            text: islandWin.transferJustFinished
                                ? (islandWin.transferFailed ? "!" : "100%")
                                : (Math.round(islandWin.activeTransferProgress * 100) + "%")
                        }
                    }

                    Rectangle {
                        anchors.fill: parent
                        radius: 24
                        visible: islandWin.mode === "call" || islandWin.mode === "notification" || islandWin.mode === "waiting" || islandWin.mode === "ready"
                        color: islandWin.mode === "call"
                            ? Qt.rgba(0.29, 0.87, 0.50, 0.22)
                            : Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.22)
                        Icon {
                            anchors.centerIn: parent
                            width: 22; height: 22
                            path: islandWin.mode === "call"
                                ? Icons.call
                                : (islandWin.mode === "notification"
                                    ? Icons.bell
                                    : (islandWin.mode === "waiting" ? Icons.send : Icons.phone))
                            color: islandWin.mode === "call" ? "#4ADE80" : Theme.primary
                        }
                    }
                }

                // Center title + subtitle + thin progress bar
                Column {
                    anchors.left: expLeftSlot.right
                    anchors.leftMargin: 12
                    anchors.right: expandedControlsRow.left
                    anchors.rightMargin: 10
                    anchors.verticalCenter: parent.verticalCenter
                    spacing: 3

                    Txt {
                        width: parent.width
                        role: "title"
                        size: 13
                        weight: Font.DemiBold
                        color: "#FFFFFF"
                        elide: Text.ElideRight
                        text: islandWin.primaryLabel
                    }

                    Txt {
                        width: parent.width
                        role: "caption"
                        size: 11
                        color: Qt.rgba(1, 1, 1, 0.68)
                        elide: Text.ElideRight
                        text: islandWin.secondaryLabel
                    }

                    // Sleek progress bar for media or transfer
                    Rectangle {
                        visible: (islandWin.mode === "media" && islandWin.mediaDuration > 0) || islandWin.mode === "transfer"
                        width: parent.width
                        height: 3.5
                        radius: 1.75
                        color: Qt.rgba(1, 1, 1, 0.16)

                        Rectangle {
                            height: parent.height
                            radius: parent.radius
                            color: islandWin.mode === "transfer" && islandWin.transferJustFinished ? islandWin.transferEndColor : Theme.primary
                            width: {
                                if (islandWin.mode === "transfer")
                                    return parent.width * (islandWin.transferJustFinished ? 1.0 : Math.max(0.04, islandWin.activeTransferProgress))
                                if (islandWin.mediaDuration > 0)
                                    return parent.width * Math.max(0, Math.min(1, islandWin.mediaPositionNow / islandWin.mediaDuration))
                                return 0
                            }
                            Behavior on width { NumberAnimation { duration: 180; easing.type: Easing.OutCubic } }
                        }
                    }
                }

                // Right action buttons in expanded pill
                Row {
                    id: expandedControlsRow
                    anchors.right: parent.right
                    anchors.verticalCenter: parent.verticalCenter
                    spacing: 6

                    // ---- Call controls: Answer (when ringing), Mute, Speaker, Hang up ----
                    Rectangle {
                        visible: islandWin.mode === "call" && PhoneCall.ringing && PhoneCall.canEnd
                        width: 32; height: 32; radius: 16
                        color: "#22C55E"
                        Icon {
                            anchors.centerIn: parent
                            width: 15; height: 15
                            path: Icons.call
                            color: "#FFFFFF"
                        }
                        HoverHandler { cursorShape: Qt.PointingHandCursor }
                        TapHandler { onTapped: PhoneCall.answer() }
                    }

                    Rectangle {
                        visible: islandWin.mode === "call" && !PhoneCall.ringing && PhoneCall.controls
                        width: 30; height: 30; radius: 15
                        color: PhoneCall.muted ? Qt.rgba(1, 1, 1, 0.26) : Qt.rgba(1, 1, 1, 0.12)
                        Icon {
                            anchors.centerIn: parent
                            width: 14; height: 14
                            path: PhoneCall.muted ? Icons.micOff : Icons.mic
                            color: "#FFFFFF"
                        }
                        HoverHandler { cursorShape: Qt.PointingHandCursor }
                        TapHandler { onTapped: PhoneCall.setMute(!PhoneCall.muted) }
                    }

                    // The call's sound on this PC (call.audio), lit while it's here.
                    Rectangle {
                        visible: islandWin.mode === "call" && !PhoneCall.ringing && PhoneCall.callAudioAvailable
                        width: 30; height: 30; radius: 15
                        color: PhoneCall.callAudio ? Qt.rgba(1, 1, 1, 0.26) : Qt.rgba(1, 1, 1, 0.12)
                        Accessible.role: Accessible.Button
                        Accessible.name: PhoneCall.callAudio ? qsTr("Back to phone") : qsTr("On PC")
                        Icon {
                            anchors.centerIn: parent
                            width: 14; height: 14
                            path: Icons.desktop
                            color: "#FFFFFF"
                        }
                        HoverHandler { cursorShape: Qt.PointingHandCursor }
                        TapHandler { onTapped: PhoneCall.moveCallAudio(!PhoneCall.callAudio) }
                    }

                    Rectangle {
                        visible: islandWin.mode === "call" && PhoneCall.canEnd
                        width: 32; height: 32; radius: 16
                        color: "#EF4444"
                        Icon {
                            anchors.centerIn: parent
                            width: 15; height: 15
                            path: Icons.callEnd
                            color: "#FFFFFF"
                        }
                        HoverHandler { cursorShape: Qt.PointingHandCursor }
                        TapHandler { onTapped: PhoneCall.end() }
                    }

                    // ---- Media controls: Previous, Play/Pause, Next ----
                    Rectangle {
                        visible: islandWin.mode === "media" && islandWin.mediaCanPrev
                        width: 28; height: 28; radius: 14
                        anchors.verticalCenter: parent.verticalCenter
                        color: expPrevHover.hovered ? Qt.rgba(1, 1, 1, 0.20) : Qt.rgba(1, 1, 1, 0.08)
                        Icon {
                            anchors.centerIn: parent
                            width: 13; height: 13
                            path: Icons.skipPrevious
                            color: "#FFFFFF"
                        }
                        HoverHandler { id: expPrevHover; cursorShape: Qt.PointingHandCursor }
                        TapHandler {
                            onTapped: MediaList.command(islandWin.mediaDeviceId, islandWin.mediaPlayerId, "previous", 0)
                        }
                    }

                    Rectangle {
                        visible: islandWin.mode === "media"
                        width: 34; height: 34; radius: 17
                        anchors.verticalCenter: parent.verticalCenter
                        color: Theme.primary
                        Icon {
                            anchors.centerIn: parent
                            width: 15; height: 15
                            path: islandWin.mediaPlaying ? Icons.pause : Icons.play
                            color: Theme.primaryContent
                        }
                        HoverHandler { cursorShape: Qt.PointingHandCursor }
                        TapHandler {
                            onTapped: MediaList.command(
                                islandWin.mediaDeviceId,
                                islandWin.mediaPlayerId,
                                islandWin.mediaPlaying ? "pause" : "play",
                                0
                            )
                        }
                    }

                    Rectangle {
                        visible: islandWin.mode === "media" && islandWin.mediaCanNext
                        width: 28; height: 28; radius: 14
                        anchors.verticalCenter: parent.verticalCenter
                        color: expNextHover.hovered ? Qt.rgba(1, 1, 1, 0.20) : Qt.rgba(1, 1, 1, 0.08)
                        Icon {
                            anchors.centerIn: parent
                            width: 13; height: 13
                            path: Icons.skipNext
                            color: "#FFFFFF"
                        }
                        HoverHandler { id: expNextHover; cursorShape: Qt.PointingHandCursor }
                        TapHandler {
                            onTapped: MediaList.command(islandWin.mediaDeviceId, islandWin.mediaPlayerId, "next", 0)
                        }
                    }

                    // ---- Transfer cancel button ----
                    Rectangle {
                        visible: islandWin.mode === "transfer" && !islandWin.transferJustFinished && islandWin.activeTransferId.length > 0
                        width: 28; height: 28; radius: 14
                        anchors.verticalCenter: parent.verticalCenter
                        color: expCancelHover.hovered ? Qt.rgba(1, 0.3, 0.3, 0.28) : Qt.rgba(1, 1, 1, 0.10)
                        Icon {
                            anchors.centerIn: parent
                            width: 13; height: 13
                            path: Icons.close
                            color: "#FFFFFF"
                        }
                        HoverHandler { id: expCancelHover; cursorShape: Qt.PointingHandCursor }
                        TapHandler {
                            onTapped: TransferList.cancel(islandWin.activeTransferId)
                        }
                    }
                }
            }

            // ================================================================
            // MULTI-DEVICE DRAG & DROP ZONE VIEW (476×128px)
            // ================================================================
            Item {
                id: dropZoneView
                anchors.fill: parent
                anchors.leftMargin: 14
                anchors.rightMargin: 14
                anchors.topMargin: 10
                anchors.bottomMargin: 10
                visible: islandWin.dropZoneOpen
                opacity: islandWin.dropZoneOpen ? 1.0 : 0.0
                Behavior on opacity { NumberAnimation { duration: 150 } }

                // Top header strip: LinkHub badge + live drop prompt + Open app button
                Row {
                    id: dropHeader
                    anchors.top: parent.top
                    anchors.left: parent.left
                    anchors.right: parent.right
                    height: 28
                    spacing: 8

                    Rectangle {
                        width: 24; height: 24; radius: 12
                        anchors.verticalCenter: parent.verticalCenter
                        color: islandWin.dropFeedbackText.length > 0
                            ? Qt.rgba(0.29, 0.87, 0.50, 0.28)
                            : Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.28)
                        Icon {
                            anchors.centerIn: parent
                            width: 13; height: 13
                            path: islandWin.dropFeedbackText.length > 0 ? Icons.check : Icons.mark
                            color: islandWin.dropFeedbackText.length > 0 ? "#4ADE80" : "#60A5FA"
                        }
                    }

                    Txt {
                        anchors.verticalCenter: parent.verticalCenter
                        width: dropHeader.width - 230
                        role: "label"
                        size: 12.5
                        weight: Font.DemiBold
                        color: islandWin.dropFeedbackText.length > 0 ? "#4ADE80" : "#FFFFFF"
                        elide: Text.ElideRight
                        text: {
                            if (islandWin.dropFeedbackText.length > 0)
                                return islandWin.dropFeedbackText
                            if (islandWin.dragActive)
                                return qsTr("Lepas file ke perangkat tujuan")
                            return qsTr("LinkHub Island · Seret / Klik")
                        }
                    }

                    // 1-Click Continuity Camera ("Ambil Foto dari HP")
                    Rectangle {
                        width: 24; height: 24; radius: 12
                        anchors.verticalCenter: parent.verticalCenter
                        color: camPhotoHover.hovered ? Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.45) : Qt.rgba(1, 1, 1, 0.10)
                        Icon {
                            anchors.centerIn: parent
                            width: 12; height: 12
                            path: Icons.camera
                            color: "#FFFFFF"
                        }
                        HoverHandler { id: camPhotoHover; cursorShape: Qt.PointingHandCursor }
                        TapHandler {
                            onTapped: {
                                AppController.startContinuityCamera("photo")
                                islandWin.dropFeedbackText = qsTr("Membuka kamera di HP…")
                                dropFeedbackTimer.restart()
                            }
                        }
                    }

                    // 1-Click Continuity Document Scanner ("Scan Dokumen dari HP")
                    Rectangle {
                        width: 24; height: 24; radius: 12
                        anchors.verticalCenter: parent.verticalCenter
                        color: camScanHover.hovered ? Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.45) : Qt.rgba(1, 1, 1, 0.10)
                        Icon {
                            anchors.centerIn: parent
                            width: 12; height: 12
                            path: Icons.scan
                            color: "#FFFFFF"
                        }
                        HoverHandler { id: camScanHover; cursorShape: Qt.PointingHandCursor }
                        TapHandler {
                            onTapped: {
                                AppController.startContinuityCamera("scan")
                                islandWin.dropFeedbackText = qsTr("Membuka pemindai dokumen di HP…")
                                dropFeedbackTimer.restart()
                            }
                        }
                    }

                    // 1-Click App & URL Handoff / Broadcast Clipboard
                    Rectangle {
                        width: 24; height: 24; radius: 12
                        anchors.verticalCenter: parent.verticalCenter
                        color: clipMeshHover.hovered ? Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.45) : Qt.rgba(1, 1, 1, 0.10)
                        Icon {
                            anchors.centerIn: parent
                            width: 12; height: 12
                            path: Icons.clipboard
                            color: "#FFFFFF"
                        }
                        HoverHandler { id: clipMeshHover; cursorShape: Qt.PointingHandCursor }
                        TapHandler {
                            onTapped: {
                                AppController.sendClipboard("mesh")
                                islandWin.dropFeedbackText = qsTr("Papan klip dikirim ke seluruh Mesh")
                                dropFeedbackTimer.restart()
                            }
                        }
                    }

                    Rectangle {
                        width: 54; height: 24; radius: 12
                        anchors.verticalCenter: parent.verticalCenter
                        color: openBtnHover.hovered ? Qt.rgba(1, 1, 1, 0.20) : Qt.rgba(1, 1, 1, 0.10)
                        Txt {
                            anchors.centerIn: parent
                            role: "caption"
                            size: 11
                            weight: Font.DemiBold
                            color: "#FFFFFF"
                            text: qsTr("Buka")
                        }
                        HoverHandler { id: openBtnHover; cursorShape: Qt.PointingHandCursor }
                        TapHandler { onTapped: AppController.activateRequested() }
                    }

                    Rectangle {
                        width: 24; height: 24; radius: 12
                        anchors.verticalCenter: parent.verticalCenter
                        color: hideIslandHover.hovered ? Qt.rgba(0.94, 0.27, 0.27, 0.35) : Qt.rgba(1, 1, 1, 0.10)
                        Icon {
                            anchors.centerIn: parent
                            width: 11; height: 11
                            path: Icons.close
                            color: "#FFFFFF"
                        }
                        HoverHandler { id: hideIslandHover; cursorShape: Qt.PointingHandCursor }
                        TapHandler { onTapped: Preferences.nectarIsland = false }
                    }
                }

                // Device drop target strip (all paired devices in the mesh ecosystem)
                Flickable {
                    id: deviceDropStrip
                    anchors.top: dropHeader.bottom
                    anchors.topMargin: 10
                    anchors.left: parent.left
                    anchors.right: parent.right
                    anchors.bottom: parent.bottom
                    contentWidth: deviceDropRow.width
                    contentHeight: height
                    clip: true
                    flickableDirection: Flickable.HorizontalFlick

                    Row {
                        id: deviceDropRow
                        height: deviceDropStrip.height
                        spacing: 10

                        // Broadcast to All Online Devices in Mesh ("Semua Mesh")
                        Rectangle {
                            id: meshAllDropCard
                            visible: DeviceList.count > 1
                            readonly property bool dropHover: meshAllDropArea.containsDrag
                            readonly property bool pointerHover: meshAllHover.hovered
                            width: 138
                            height: deviceDropRow.height - 2
                            radius: 16
                            color: dropHover
                                ? Theme.primary
                                : (pointerHover ? Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.30)
                                                : Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.18))
                            border.width: dropHover ? 1.5 : 1
                            border.color: dropHover
                                ? "#FFFFFF"
                                : Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.55)
                            scale: dropHover ? 1.04 : 1.0
                            Behavior on scale { NumberAnimation { duration: 140; easing.type: Easing.OutCubic } }
                            Behavior on color { ColorAnimation { duration: 130 } }

                            Row {
                                anchors.fill: parent
                                anchors.leftMargin: 10
                                anchors.rightMargin: 10
                                spacing: 8

                                Rectangle {
                                    width: 30; height: 30; radius: 10
                                    anchors.verticalCenter: parent.verticalCenter
                                    color: meshAllDropCard.dropHover
                                        ? Qt.rgba(1, 1, 1, 0.24)
                                        : Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.38)
                                    Icon {
                                        anchors.centerIn: parent
                                        width: 15; height: 15
                                        path: Icons.mesh
                                        color: "#FFFFFF"
                                    }
                                }

                                Column {
                                    anchors.verticalCenter: parent.verticalCenter
                                    width: meshAllDropCard.width - 58
                                    spacing: 1
                                    Txt {
                                        width: parent.width
                                        role: "label"
                                        size: 12
                                        weight: Font.DemiBold
                                        color: "#FFFFFF"
                                        elide: Text.ElideRight
                                        text: qsTr("Semua Mesh")
                                    }
                                    Txt {
                                        width: parent.width
                                        role: "caption"
                                        size: 10
                                        color: "#93C5FD"
                                        elide: Text.ElideRight
                                        text: meshAllDropCard.dropHover
                                            ? qsTr("Lepas ke semua")
                                            : qsTr("%1 perangkat").arg(DeviceList.count)
                                    }
                                }
                            }

                            HoverHandler {
                                id: meshAllHover
                                cursorShape: Qt.PointingHandCursor
                            }

                            TapHandler {
                                onTapped: {
                                    islandWin.quickPickDeviceId = "mesh"
                                    islandWin.quickPickDeviceName = qsTr("Semua Perangkat Mesh")
                                    islandFilePicker.open()
                                }
                            }

                            DropArea {
                                id: meshAllDropArea
                                anchors.fill: parent
                                onEntered: (drag) => {
                                    islandWin.hoveredDropDeviceId = "mesh"
                                    drag.acceptProposedAction()
                                }
                                onExited: {
                                    if (islandWin.hoveredDropDeviceId === "mesh")
                                        islandWin.hoveredDropDeviceId = ""
                                }
                                onDropped: (drop) => {
                                    const urls = islandWin.extractDropUrls(drop)
                                    const text = drop.text ? String(drop.text) : ""
                                    islandWin.hoveredDropDeviceId = ""
                                    islandWin.sendDroppedToDevice("mesh", qsTr("Semua Perangkat Mesh"), urls, text)
                                    drop.acceptProposedAction()
                                }
                            }
                        }

                        Repeater {
                            model: DeviceList
                            delegate: Rectangle {
                                id: devDropCard
                                required property string deviceId
                                required property string name
                                required property string kind
                                required property bool online

                                readonly property bool dropHover: chipDropArea.containsDrag
                                readonly property bool pointerHover: chipHover.hovered
                                width: Math.max(124, Math.min(164, devLabelCol.implicitWidth + 52))
                                height: deviceDropRow.height - 2
                                radius: 16
                                color: dropHover
                                    ? Theme.primary
                                    : (pointerHover ? Qt.rgba(1, 1, 1, 0.14) : Qt.rgba(1, 1, 1, 0.08))
                                border.width: dropHover ? 1.5 : 1
                                border.color: dropHover
                                    ? "#FFFFFF"
                                    : (online ? Qt.rgba(0.29, 0.87, 0.50, 0.40) : Qt.rgba(1, 1, 1, 0.14))
                                scale: dropHover ? 1.04 : 1.0
                                Behavior on scale { NumberAnimation { duration: 140; easing.type: Easing.OutCubic } }
                                Behavior on color { ColorAnimation { duration: 130 } }

                                Row {
                                    anchors.fill: parent
                                    anchors.leftMargin: 10
                                    anchors.rightMargin: 10
                                    spacing: 8

                                    Rectangle {
                                        width: 30; height: 30; radius: 10
                                        anchors.verticalCenter: parent.verticalCenter
                                        color: devDropCard.dropHover
                                            ? Qt.rgba(1, 1, 1, 0.24)
                                            : Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.26)
                                        Icon {
                                            anchors.centerIn: parent
                                            width: 15; height: 15
                                            path: (devDropCard.kind === "phone" || devDropCard.kind === "tablet")
                                                ? Icons.phone
                                                : (devDropCard.kind === "laptop" ? Icons.laptop : Icons.desktop)
                                            color: "#FFFFFF"
                                        }
                                        Rectangle {
                                            width: 8; height: 8; radius: 4
                                            anchors.right: parent.right
                                            anchors.bottom: parent.bottom
                                            anchors.rightMargin: -1
                                            anchors.bottomMargin: -1
                                            color: devDropCard.online ? "#4ADE80" : "#6B7280"
                                            border.width: 1.5
                                            border.color: "#0D0E13"
                                        }
                                    }

                                    Column {
                                        id: devLabelCol
                                        anchors.verticalCenter: parent.verticalCenter
                                        width: Math.max(60, devDropCard.width - 58)
                                        spacing: 1
                                        Txt {
                                            width: parent.width
                                            role: "label"
                                            size: 12
                                            weight: Font.DemiBold
                                            color: "#FFFFFF"
                                            elide: Text.ElideRight
                                            text: devDropCard.name
                                        }
                                        Txt {
                                            width: parent.width
                                            role: "caption"
                                            size: 10
                                            color: devDropCard.dropHover
                                                ? "#FFFFFF"
                                                : (devDropCard.online ? "#86EFAC" : Qt.rgba(1, 1, 1, 0.55))
                                            elide: Text.ElideRight
                                            text: devDropCard.dropHover
                                                ? qsTr("Lepas di sini")
                                                : (devDropCard.online ? qsTr("Online") : qsTr("Antre offline"))
                                        }
                                    }
                                }

                                HoverHandler {
                                    id: chipHover
                                    cursorShape: Qt.PointingHandCursor
                                }

                                TapHandler {
                                    onTapped: {
                                        islandWin.quickPickDeviceId = devDropCard.deviceId
                                        islandWin.quickPickDeviceName = devDropCard.name
                                        islandFilePicker.open()
                                    }
                                }

                                DropArea {
                                    id: chipDropArea
                                    anchors.fill: parent
                                    onEntered: (drag) => {
                                        islandWin.hoveredDropDeviceId = devDropCard.deviceId
                                        drag.acceptProposedAction()
                                    }
                                    onExited: {
                                        if (islandWin.hoveredDropDeviceId === devDropCard.deviceId)
                                            islandWin.hoveredDropDeviceId = ""
                                    }
                                    onDropped: (drop) => {
                                        const urls = islandWin.extractDropUrls(drop)
                                        const text = drop.text ? String(drop.text) : ""
                                        islandWin.hoveredDropDeviceId = ""
                                        islandWin.sendDroppedToDevice(devDropCard.deviceId, devDropCard.name, urls, text)
                                        drop.acceptProposedAction()
                                    }
                                }
                            }
                        }

                        // Empty state when no paired devices exist yet
                        Rectangle {
                            visible: DeviceList.count === 0
                            width: deviceDropStrip.width
                            height: deviceDropRow.height - 2
                            radius: 16
                            color: Qt.rgba(1, 1, 1, 0.06)
                            border.width: 1
                            border.color: Qt.rgba(1, 1, 1, 0.12)
                            Txt {
                                anchors.centerIn: parent
                                role: "caption"
                                size: 12
                                color: Qt.rgba(1, 1, 1, 0.72)
                                text: qsTr("Belum ada perangkat terpasang · Klik Buka untuk memasangkan")
                            }
                        }
                    }
                }
            }
        }
    }
}
