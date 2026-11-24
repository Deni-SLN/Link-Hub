// SPDX-License-Identifier: GPL-3.0-or-later
import QtQuick
import QtQuick.Dialogs
import QtQuick.Layouts
import app.nectarlink
import "../components"

// Ekosistem LinkHub Mesh: pusat komando ekosistem multi-perangkat (PC, HP, Tablet)
// lengkap dengan Visual Super Device Topology, Mesh Broadcast (Kirim File & Clipboard
// ke Semua), Continuity Camera & Scanner, App/URL Handoff, Cross-Device Media & Audio Hub,
// Smart Hotspot 1-Klik, dan kontrol LinkHub Island.
Item {
    id: page
    property bool active: true
    visible: active
    signal pairRequested()
    signal openDeviceRequested(int deviceIndex)

    property string pickerDeviceId: ""
    property string pickerDeviceName: ""
    property string dropFeedbackText: ""
    property string hoveredCardId: ""

    readonly property int onlineDevicesCount: {
        let count = 0
        for (let i = 0; i < meshRepeater.count; i++) {
            const item = meshRepeater.itemAt(i)
            if (item && item.online) count++
        }
        return count
    }

    Timer {
        id: feedbackTimer
        interval: 2800
        repeat: false
        onTriggered: page.dropFeedbackText = ""
    }

    function showFeedback(msg) {
        page.dropFeedbackText = msg
        feedbackTimer.restart()
    }

    function extractUrls(drop) {
        const urls = []
        if (drop && drop.urls) {
            for (let i = 0; i < drop.urls.length; i++) {
                urls.push(drop.urls[i].toString())
            }
        }
        return urls
    }

    FileDialog {
        id: meshFilePicker
        title: page.pickerDeviceName.length > 0
            ? qsTr("Kirim ke %1").arg(page.pickerDeviceName)
            : qsTr("Kirim file")
        fileMode: FileDialog.OpenFiles
        onAccepted: {
            if (page.pickerDeviceId.length > 0 && selectedFiles.length > 0) {
                const urls = selectedFiles.map(u => u.toString())
                TransferList.send(page.pickerDeviceId, urls)
                page.showFeedback(qsTr("Mengirim %1 file ke %2…").arg(urls.length).arg(page.pickerDeviceName))
            }
        }
    }

    ScrollRail { flick: meshFlick }
    Flickable {
        id: meshFlick
        anchors.fill: parent
        contentHeight: column.height + Theme.contentPadding * 2
        boundsBehavior: Flickable.StopAtBounds
        clip: true

        Column {
            id: column
            readonly property real maxContentWidth: 1680
            width: Math.min(parent.width - Theme.contentPadding * 2, maxContentWidth)
            x: Math.round((parent.width - width) / 2)
            y: Theme.contentPadding
            spacing: Theme.gutter

            // ================================================================
            // 1. ECOSYSTEM HUB & SUPER DEVICE TOPOLOGY CARD
            // ================================================================
            Card {
                width: column.width

                Column {
                    width: parent.width
                    spacing: 16

                    RowLayout {
                        width: parent.width
                        spacing: 16

                        Rectangle {
                            Layout.preferredWidth: 58
                            Layout.preferredHeight: 58
                            radius: 18
                            color: Theme.primaryContainer
                            Icon {
                                anchors.centerIn: parent
                                path: Icons.mesh
                                width: 28
                                height: 28
                                color: Theme.primaryContainerContent
                            }
                        }

                        Column {
                            Layout.fillWidth: true
                            spacing: 4

                            Row {
                                spacing: 8
                                Txt {
                                    anchors.verticalCenter: parent.verticalCenter
                                    role: "headline"
                                    text: qsTr("Ekosistem LinkHub Mesh")
                                }
                                Chip {
                                    anchors.verticalCenter: parent.verticalCenter
                                    selected: true
                                    text: "v" + (AppController.version.length > 0 ? AppController.version : "0.2.0")
                                }
                                Chip {
                                    anchors.verticalCenter: parent.verticalCenter
                                    visible: DeviceList.count > 0
                                    selected: page.onlineDevicesCount > 0
                                    text: page.onlineDevicesCount > 0
                                        ? qsTr("%1 / %2 terhubung").arg(page.onlineDevicesCount).arg(DeviceList.count)
                                        : qsTr("%1 terpasang").arg(DeviceList.count)
                                }
                                Chip {
                                    anchors.verticalCenter: parent.verticalCenter
                                    visible: page.dropFeedbackText.length > 0
                                    selected: true
                                    iconPath: Icons.check
                                    text: page.dropFeedbackText
                                }
                            }

                            Txt {
                                width: parent.width
                                role: "bodySmall"
                                muted: true
                                wrapMode: Text.WordWrap
                                text: qsTr("PC ini (%1) & seluruh perangkat Anda otomatis terhubung dalam 1 ekosistem P2P terenkripsi (%2). Salin teks/gambar di satu perangkat untuk langsung tempel di perangkat lainnya.")
                                    .arg(AppController.deviceName.length > 0 ? AppController.deviceName : qsTr("PC ini"))
                                    .arg(Preferences.awayMode ? qsTr("Wi-Fi / LAN, USB & Jauh") : qsTr("Wi-Fi / LAN & USB"))
                            }
                        }

                        Row {
                            spacing: 8
                            Button {
                                variant: "tonal"
                                text: qsTr("Segarkan")
                                iconPath: Icons.refresh
                                onClicked: {
                                    AppController.syncNow()
                                    page.showFeedback(qsTr("Menyegarkan koneksi Mesh…"))
                                }
                            }
                            Button {
                                text: qsTr("Pasangkan perangkat")
                                iconPath: Icons.plus
                                onClicked: page.pairRequested()
                            }
                        }
                    }

                    // Visual "Super Device" Desk Topology & Mesh Broadcast Drop Zone
                    Rectangle {
                        id: superDeviceBox
                        visible: DeviceList.count > 0
                        width: parent.width
                        height: topoCol.implicitHeight + 28
                        radius: Theme.radiusLg
                        color: broadcastDrop.containsDrag
                            ? Qt.rgba(Theme.primary.r, Theme.primary.g, Theme.primary.b, 0.22)
                            : (Theme.graphite ? "transparent" : Theme.surfaceContainerLow)
                        border.width: broadcastDrop.containsDrag ? 2 : 1
                        border.color: broadcastDrop.containsDrag ? Theme.primary : Theme.outlineVariant
                        Behavior on color { ColorAnimation { duration: 150 } }

                        DropArea {
                            id: broadcastDrop
                            anchors.fill: parent
                            onEntered: (drag) => drag.acceptProposedAction()
                            onDropped: (drop) => {
                                const urls = page.extractUrls(drop)
                                if (urls.length > 0) {
                                    TransferList.send("mesh", urls)
                                    page.showFeedback(qsTr("Mengirim %1 file ke semua perangkat Mesh…").arg(urls.length))
                                } else {
                                    AppController.sendClipboard("mesh")
                                    page.showFeedback(qsTr("Papan klip dikirim ke semua perangkat Mesh"))
                                }
                                drop.acceptProposedAction()
                            }
                        }

                        // Central visual connection nodes: Satellite devices <-> This PC Hub
                        Column {
                            id: topoCol
                            anchors.centerIn: parent
                            spacing: 10
                            width: parent.width - 28

                            Flow {
                                width: parent.width
                                spacing: 12

                                // Center PC Hub Node
                                Rectangle {
                                    height: 54
                                    width: Math.max(154, hubNodeRow.implicitWidth + 28)
                                    radius: 18
                                    color: Theme.primaryContainer
                                    border.width: 1.5
                                    border.color: Theme.primary

                                    Row {
                                        id: hubNodeRow
                                        anchors.centerIn: parent
                                        spacing: 10
                                        Icon {
                                            anchors.verticalCenter: parent.verticalCenter
                                            width: 22; height: 22
                                            path: Icons.laptop
                                            color: Theme.primaryContainerContent
                                        }
                                        Column {
                                            anchors.verticalCenter: parent.verticalCenter
                                            spacing: 0
                                            Txt {
                                                role: "label"
                                                color: Theme.primaryContainerContent
                                                text: AppController.deviceName.length > 0 ? AppController.deviceName : qsTr("PC Ini")
                                            }
                                            Txt {
                                                role: "caption"
                                                color: Theme.primaryContainerContent
                                                opacity: 0.8
                                                text: qsTr("Pusat Mesh · Aktif")
                                            }
                                        }
                                    }
                                }

                                // Connected Peer Satellite Nodes
                                Repeater {
                                    model: DeviceList
                                    delegate: Row {
                                        id: satNode
                                        required property int index
                                        required property string deviceId
                                        required property string name
                                        required property string kind
                                        required property bool online
                                        required property string path
                                        required property int rttMs
                                        spacing: 10

                                        // Dashed/Solid Link Line
                                        Rectangle {
                                            width: 24
                                            height: 2
                                            anchors.verticalCenter: parent.verticalCenter
                                            color: satNode.online ? Theme.primary : Theme.outlineVariant
                                        }

                                        Rectangle {
                                            height: 54
                                            width: Math.max(146, satRow.implicitWidth + 26)
                                            radius: 18
                                            color: satNode.online ? Theme.secondaryContainer : Theme.surfaceContainerHigh
                                            border.width: 1
                                            border.color: satNode.online ? Theme.primary : Theme.outlineVariant

                                            Row {
                                                id: satRow
                                                anchors.centerIn: parent
                                                spacing: 8
                                                Icon {
                                                    anchors.verticalCenter: parent.verticalCenter
                                                    width: 18; height: 18
                                                    path: (satNode.kind === "phone" || satNode.kind === "tablet")
                                                        ? Icons.phone : Icons.laptop
                                                    color: satNode.online ? Theme.secondaryContainerContent : Theme.surfaceContentVariant
                                                }
                                                Column {
                                                    anchors.verticalCenter: parent.verticalCenter
                                                    spacing: 0
                                                    Txt {
                                                        role: "label"
                                                        text: satNode.name
                                                    }
                                                    Txt {
                                                        role: "caption"
                                                        muted: true
                                                        text: satNode.online
                                                            ? (satNode.path === "usb" ? qsTr("USB · %1 ms").arg(Math.max(1, satNode.rttMs))
                                                                                      : qsTr("Wi-Fi · %1 ms").arg(Math.max(1, satNode.rttMs)))
                                                            : qsTr("Siaga")
                                                    }
                                                }
                                                StatusDot {
                                                    anchors.verticalCenter: parent.verticalCenter
                                                    online: satNode.online
                                                }
                                            }

                                            MouseArea {
                                                anchors.fill: parent
                                                cursorShape: Qt.PointingHandCursor
                                                onClicked: page.openDeviceRequested(satNode.index)
                                            }
                                        }
                                    }
                                }
                            }

                            Txt {
                                anchors.horizontalCenter: parent.horizontalCenter
                                role: "caption"
                                muted: true
                                text: broadcastDrop.containsDrag
                                    ? qsTr("Lepas file di sini untuk Broadcast ke SEMUA perangkat dalam ekosistem Mesh")
                                    : qsTr("Tip: Seret & lepas file ke kotak ini untuk mengirim ke semua perangkat sekaligus, atau klik perangkat untuk membuka kontrolnya.")
                            }
                        }
                    }

                    // Ecosystem-Wide Quick Actions Bar (Broadcast File, Broadcast Clipboard, Handoff Link, Continuity Camera & Scan)
                    Flow {
                        visible: DeviceList.count > 0
                        width: parent.width
                        spacing: 8

                        Button {
                            variant: "fill"
                            size: "sm"
                            iconPath: Icons.send
                            text: qsTr("Broadcast File ke Semua (%1)").arg(DeviceList.count)
                            onClicked: {
                                page.pickerDeviceId = "mesh"
                                page.pickerDeviceName = qsTr("Semua Perangkat Mesh")
                                meshFilePicker.open()
                            }
                        }

                        Button {
                            variant: "tonal"
                            size: "sm"
                            iconPath: Icons.clipboard
                            text: qsTr("Broadcast Papan Klip ke Semua")
                            onClicked: {
                                AppController.sendClipboard("mesh")
                                page.showFeedback(qsTr("Papan klip dikirim ke seluruh ekosistem Mesh"))
                            }
                        }

                        Button {
                            variant: "tonal"
                            size: "sm"
                            iconPath: Icons.globe
                            text: qsTr("Handoff Link / Peta ke HP")
                            onClicked: {
                                AppController.openLinkOnPhone("mesh", "")
                                page.showFeedback(qsTr("Membuka tautan dari papan klip di HP…"))
                            }
                        }

                        Button {
                            variant: "tonal"
                            size: "sm"
                            iconPath: Icons.camera
                            text: AppController.continuityCameraBusy
                                ? AppController.continuityCameraStatus
                                : qsTr("Ambil Foto dari HP")
                            onClicked: {
                                AppController.startContinuityCamera("photo")
                                page.showFeedback(qsTr("Membuka kamera di HP…"))
                            }
                        }

                        Button {
                            variant: "tonal"
                            size: "sm"
                            iconPath: Icons.scan
                            text: qsTr("Scan Dokumen dari HP")
                            onClicked: {
                                AppController.startContinuityCamera("scan")
                                page.showFeedback(qsTr("Membuka pemindai dokumen di HP…"))
                            }
                        }
                    }

                    Divider {
                        width: parent.width
                    }

                    // Toggles Row: LinkHub Island + Universal PC Audio Streaming
                    RowLayout {
                        width: parent.width
                        spacing: 12

                        Column {
                            Layout.fillWidth: true
                            spacing: 2
                            Txt {
                                role: "title"
                                text: qsTr("LinkHub Island (Drag & Drop di Sebelah Kalender & Jam)")
                            }
                            Txt {
                                width: parent.width
                                role: "bodySmall"
                                muted: true
                                wrapMode: Text.WordWrap
                                text: qsTr("Tampilkan kapsul interaktif di bar atas sebelah kalender & jam untuk seret & lepas file ke perangkat mana pun, kamera cepat, dan kontrol media.")
                            }
                        }

                        Toggle {
                            checked: Preferences.nectarIsland
                            onToggled: v => Preferences.nectarIsland = v
                        }
                    }

                    Divider {
                        width: parent.width
                    }

                    RowLayout {
                        width: parent.width
                        spacing: 12

                        Column {
                            Layout.fillWidth: true
                            spacing: 2
                            Txt {
                                role: "title"
                                text: qsTr("Jadikan HP sebagai Speaker Nirkabel PC (Audio Flow)")
                            }
                            Txt {
                                width: parent.width
                                role: "bodySmall"
                                muted: true
                                wrapMode: Text.WordWrap
                                text: qsTr("Alirkan suara dari PC ini langsung ke speaker atau earphone HP yang sedang terhubung di ekosistem Mesh.")
                            }
                        }

                        Toggle {
                            checked: Preferences.streamPcAudioToPhone
                            onToggled: v => Preferences.streamPcAudioToPhone = v
                        }
                    }

                    Divider {
                        width: parent.width
                    }

                    RowLayout {
                        width: parent.width
                        spacing: 12

                        Column {
                            Layout.fillWidth: true
                            spacing: 2
                            Txt {
                                role: "title"
                                text: qsTr("Surround Audio (Multi-Device Speaker)")
                            }
                            Txt {
                                width: parent.width
                                role: "bodySmall"
                                muted: true
                                wrapMode: Text.WordWrap
                                text: qsTr("Pancarkan audio PC ke seluruh perangkat Mesh secara bersamaan. Setiap perangkat dapat memutar Stereo penuh atau diatur sebagai channel Kiri/Kanan.")
                            }
                        }

                        Toggle {
                            checked: Preferences.surroundAudio
                            onToggled: v => {
                                Preferences.surroundAudio = v
                                if (v && !Preferences.streamPcAudioToPhone) {
                                    Preferences.streamPcAudioToPhone = true
                                }
                            }
                        }
                    }

                    Column {
                        width: parent.width
                        spacing: 10
                        visible: Preferences.surroundAudio && DeviceList.count > 0

                        Txt {
                            role: "caption"
                            muted: true
                            text: qsTr("Atur Peran Audio Per Perangkat:")
                        }

                        Repeater {
                            model: DeviceList
                            delegate: RowLayout {
                                id: devRoleRow
                                required property int index
                                required property string deviceId
                                required property string name
                                required property bool online
                                width: parent.width
                                spacing: 12

                                Row {
                                    Layout.fillWidth: true
                                    spacing: 8
                                    StatusDot {
                                        anchors.verticalCenter: parent.verticalCenter
                                        online: devRoleRow.online
                                    }
                                    Txt {
                                        anchors.verticalCenter: parent.verticalCenter
                                        role: "body"
                                        text: devRoleRow.name
                                        elide: Text.ElideRight
                                    }
                                }

                                Segmented {
                                    value: Preferences.surroundRoleOf(devRoleRow.deviceId)
                                    options: [
                                        { value: "stereo", label: qsTr("Stereo") },
                                        { value: "left", label: qsTr("Kiri") },
                                        { value: "right", label: qsTr("Kanan") }
                                    ]
                                    onPicked: val => Preferences.setSurroundRole(devRoleRow.deviceId, val)
                                }
                            }
                        }
                    }
                }
            }

            // ================================================================
            // 2. CROSS-DEVICE MEDIA & AUDIO HUB CARD (Active players across Mesh)
            // ================================================================
            Card {
                width: column.width
                visible: MediaList.count > 0

                Column {
                    width: parent.width
                    spacing: 12

                    Row {
                        spacing: 8
                        Icon {
                            anchors.verticalCenter: parent.verticalCenter
                            width: 20; height: 20
                            path: Icons.music
                            color: Theme.primary
                        }
                        Txt {
                            anchors.verticalCenter: parent.verticalCenter
                            role: "title"
                            text: qsTr("Pusat Media & Audio Lintas Ekosistem (%1)").arg(MediaList.count)
                        }
                    }

                    Repeater {
                        model: MediaList
                        delegate: Rectangle {
                            id: mediaRow
                            required property string deviceId
                            required property string playerId
                            required property string app
                            required property string title
                            required property string artist
                            required property bool playing
                            required property bool canPlay
                            required property bool canPause
                            required property bool canNext
                            required property bool canPrevious

                            width: parent.width
                            height: 54
                            radius: Theme.radiusMd
                            color: Theme.graphite ? "transparent" : Theme.surfaceContainerLow
                            border.width: 1
                            border.color: Theme.outlineVariant

                            RowLayout {
                                anchors.fill: parent
                                anchors.leftMargin: 12
                                anchors.rightMargin: 12
                                spacing: 12

                                Rectangle {
                                    Layout.preferredWidth: 36
                                    Layout.preferredHeight: 36
                                    radius: 10
                                    color: mediaRow.playing ? Theme.primaryContainer : Theme.surfaceContainerHigh
                                    Icon {
                                        anchors.centerIn: parent
                                        width: 18; height: 18
                                        path: Icons.music
                                        color: mediaRow.playing ? Theme.primaryContainerContent : Theme.surfaceContentVariant
                                    }
                                }

                                Column {
                                    Layout.fillWidth: true
                                    spacing: 1
                                    Txt {
                                        width: parent.width
                                        role: "label"
                                        text: mediaRow.title.length > 0 ? mediaRow.title : (mediaRow.app.length > 0 ? mediaRow.app : qsTr("Media"))
                                        elide: Text.ElideRight
                                    }
                                    Txt {
                                        width: parent.width
                                        role: "caption"
                                        muted: true
                                        elide: Text.ElideRight
                                        text: {
                                            const devLabel = mediaRow.deviceId === "local"
                                                ? qsTr("PC Ini")
                                                : (DeviceList.rowOf(mediaRow.deviceId) >= 0
                                                    ? DeviceList.deviceNameAt(DeviceList.rowOf(mediaRow.deviceId))
                                                    : qsTr("Perangkat Mesh"))
                                            const parts = [devLabel]
                                            if (mediaRow.artist.length > 0) parts.push(mediaRow.artist)
                                            if (mediaRow.app.length > 0) parts.push(mediaRow.app)
                                            return parts.join(" · ")
                                        }
                                    }
                                }

                                Row {
                                    spacing: 6
                                    Button {
                                        visible: mediaRow.canPrevious
                                        variant: "tonal"
                                        size: "sm"
                                        iconPath: Icons.skipPrevious
                                        text: ""
                                        onClicked: MediaList.command(mediaRow.deviceId, mediaRow.playerId, "previous", 0)
                                    }
                                    Button {
                                        variant: mediaRow.playing ? "tonal" : "fill"
                                        size: "sm"
                                        iconPath: mediaRow.playing ? Icons.pause : Icons.play
                                        text: mediaRow.playing ? qsTr("Jeda") : qsTr("Putar")
                                        onClicked: MediaList.command(mediaRow.deviceId, mediaRow.playerId, mediaRow.playing ? "pause" : "play", 0)
                                    }
                                    Button {
                                        visible: mediaRow.canNext
                                        variant: "tonal"
                                        size: "sm"
                                        iconPath: Icons.skipNext
                                        text: ""
                                        onClicked: MediaList.command(mediaRow.deviceId, mediaRow.playerId, "next", 0)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================================================================
            // 3. EMPTY STATE WHEN NO DEVICES ARE PAIRED YET
            // ================================================================
            Card {
                width: column.width
                visible: DeviceList.count === 0

                Column {
                    width: parent.width
                    spacing: 10
                    Txt {
                        role: "title"
                        text: qsTr("Belum ada perangkat di ekosistem Mesh Anda")
                    }
                    Txt {
                        width: parent.width
                        role: "body"
                        muted: true
                        wrapMode: Text.WordWrap
                        text: qsTr("Pasangkan HP, tablet, atau PC lain sekali saja. Perangkat tepercaya otomatis saling mengenalkan diri dan terhubung langsung dalam 1 ekosistem LinkHub Mesh.")
                    }
                    Button {
                        text: qsTr("Pasangkan perangkat pertama")
                        iconPath: Icons.plus
                        onClicked: page.pairRequested()
                    }
                }
            }

            // ================================================================
            // 4. CONNECTED MESH NODES GRID (Individual Per-Device Cards + Drag & Drop)
            // ================================================================
            Txt {
                visible: DeviceList.count > 0
                role: "title"
                text: qsTr("Perangkat dalam Ekosistem (%1) · Seret file langsung ke kartu perangkat").arg(DeviceList.count)
            }

            Grid {
                id: meshGrid
                width: column.width
                visible: DeviceList.count > 0
                columns: width > 680 ? 2 : 1
                spacing: 12
                readonly property real cardW: (width - spacing * (columns - 1)) / columns

                Repeater {
                    id: meshRepeater
                    model: DeviceList
                    delegate: Card {
                        id: nodeCard
                        width: meshGrid.cardW
                        required property int index
                        required property string deviceId
                        required property string name
                        required property string kind
                        required property string os
                        required property string model
                        required property bool online
                        required property string path
                        required property int rttMs
                        required property int battery
                        required property bool charging

                        readonly property bool ringing: AppController.ringingDevices.includes(nodeCard.deviceId)
                        readonly property bool isPhoneOrTablet: nodeCard.kind === "phone" || nodeCard.kind === "tablet"
                        readonly property string phoneAudioState: MediaList.revision >= 0 ? MediaList.phoneSoundState(nodeCard.deviceId) : "off"
                        readonly property string pathLabel: {
                            if (!nodeCard.online) return qsTr("Luring")
                            if (nodeCard.path === "usb") return nodeCard.rttMs > 0 ? qsTr("USB · %1 ms").arg(nodeCard.rttMs) : qsTr("USB")
                            if (nodeCard.path === "relay") return nodeCard.rttMs > 0 ? qsTr("Relay Jauh · %1 ms").arg(nodeCard.rttMs) : qsTr("Relay Jauh")
                            return nodeCard.rttMs > 0 ? qsTr("Wi-Fi / LAN · %1 ms").arg(nodeCard.rttMs) : qsTr("Wi-Fi / LAN")
                        }

                        // Per-card Drag & Drop overlay
                        DropArea {
                            id: cardDropArea
                            anchors.fill: parent
                            onEntered: (drag) => {
                                page.hoveredCardId = nodeCard.deviceId
                                drag.acceptProposedAction()
                            }
                            onExited: {
                                if (page.hoveredCardId === nodeCard.deviceId)
                                    page.hoveredCardId = ""
                            }
                            onDropped: (drop) => {
                                page.hoveredCardId = ""
                                const urls = page.extractUrls(drop)
                                if (urls.length > 0) {
                                    TransferList.send(nodeCard.deviceId, urls)
                                    page.showFeedback(qsTr("Mengirim %1 file ke %2…").arg(urls.length).arg(nodeCard.name))
                                } else {
                                    AppController.sendClipboard(nodeCard.deviceId)
                                    page.showFeedback(qsTr("Papan klip dikirim ke %1").arg(nodeCard.name))
                                }
                                drop.acceptProposedAction()
                            }
                        }

                        Column {
                            width: parent.width
                            spacing: 12

                            RowLayout {
                                width: parent.width
                                spacing: 12

                                Rectangle {
                                    Layout.preferredWidth: 48
                                    Layout.preferredHeight: 48
                                    radius: 14
                                    color: cardDropArea.containsDrag
                                        ? Theme.primary
                                        : (nodeCard.online ? Theme.primaryContainer : Theme.surfaceContainerHigh)
                                    Icon {
                                        anchors.centerIn: parent
                                        width: 22
                                        height: 22
                                        path: nodeCard.isPhoneOrTablet
                                            ? Icons.phone
                                            : (nodeCard.kind === "laptop" ? Icons.laptop : Icons.desktop)
                                        color: cardDropArea.containsDrag
                                            ? Theme.primaryContent
                                            : (nodeCard.online ? Theme.primaryContainerContent : Theme.surfaceContentVariant)
                                    }
                                    StatusDot {
                                        anchors.right: parent.right
                                        anchors.bottom: parent.bottom
                                        anchors.rightMargin: -2
                                        anchors.bottomMargin: -2
                                        online: nodeCard.online
                                    }
                                }

                                Column {
                                    Layout.fillWidth: true
                                    spacing: 2
                                    Txt {
                                        width: parent.width
                                        role: "title"
                                        text: nodeCard.name
                                        elide: Text.ElideRight
                                    }
                                    Txt {
                                        width: parent.width
                                        role: "bodySmall"
                                        muted: true
                                        elide: Text.ElideRight
                                        text: {
                                            if (cardDropArea.containsDrag)
                                                return qsTr("Lepas file untuk langsung mengirim ke %1").arg(nodeCard.name)
                                            const parts = [nodeCard.pathLabel]
                                            if (nodeCard.model && nodeCard.model.length > 0) parts.push(nodeCard.model)
                                            else if (nodeCard.os.length > 0) parts.push(nodeCard.os)
                                            if (nodeCard.online && nodeCard.battery >= 0) {
                                                parts.push(nodeCard.charging
                                                    ? qsTr("Baterai %1% · mengisi").arg(nodeCard.battery)
                                                    : qsTr("Baterai %1%").arg(nodeCard.battery))
                                            }
                                            return parts.join(" · ")
                                        }
                                    }
                                }

                                Chip {
                                    selected: nodeCard.online || cardDropArea.containsDrag
                                    text: cardDropArea.containsDrag
                                        ? qsTr("Lepas di sini")
                                        : (nodeCard.online ? qsTr("1 Ekosistem") : qsTr("Terpasang"))
                                }
                            }

                            // Rich Continuity Action Buttons per Device
                            Flow {
                                width: parent.width
                                spacing: 8

                                Button {
                                    variant: "fill"
                                    size: "sm"
                                    text: qsTr("Layar & Kontrol")
                                    iconPath: Icons.mirror
                                    enabled: nodeCard.online
                                    onClicked: {
                                        Mirror.start(nodeCard.deviceId)
                                        page.showFeedback(qsTr("Membuka layar %1…").arg(nodeCard.name))
                                    }
                                }

                                Button {
                                    variant: "tonal"
                                    size: "sm"
                                    text: qsTr("Kirim file")
                                    iconPath: Icons.send
                                    onClicked: {
                                        page.pickerDeviceId = nodeCard.deviceId
                                        page.pickerDeviceName = nodeCard.name
                                        meshFilePicker.open()
                                    }
                                }

                                Button {
                                    variant: "tonal"
                                    size: "sm"
                                    text: qsTr("Papan klip")
                                    iconPath: Icons.clipboard
                                    onClicked: {
                                        AppController.sendClipboard(nodeCard.deviceId)
                                        page.showFeedback(qsTr("Papan klip dikirim ke %1").arg(nodeCard.name))
                                    }
                                }

                                Button {
                                    visible: nodeCard.isPhoneOrTablet
                                    variant: "tonal"
                                    size: "sm"
                                    text: qsTr("Buka Link")
                                    iconPath: Icons.globe
                                    onClicked: {
                                        AppController.openLinkOnPhone(nodeCard.deviceId, "")
                                        page.showFeedback(qsTr("Mengirim tautan papan klip ke %1…").arg(nodeCard.name))
                                    }
                                }

                                Button {
                                    visible: nodeCard.isPhoneOrTablet
                                    variant: "tonal"
                                    size: "sm"
                                    text: qsTr("Hotspot")
                                    iconPath: Icons.wifi
                                    enabled: nodeCard.online
                                    onClicked: {
                                        AppController.setPhoneToggle(nodeCard.deviceId, "hotspot", "true")
                                        page.showFeedback(qsTr("Mengaktifkan Hotspot di %1…").arg(nodeCard.name))
                                    }
                                }

                                Button {
                                    visible: nodeCard.isPhoneOrTablet
                                    variant: nodeCard.phoneAudioState !== "off" ? "fill" : "tonal"
                                    size: "sm"
                                    text: nodeCard.phoneAudioState !== "off" ? qsTr("Suara HP: Aktif") : qsTr("Suara HP di PC")
                                    iconPath: Icons.speaker
                                    enabled: nodeCard.online
                                    onClicked: {
                                        const nextOn = nodeCard.phoneAudioState === "off"
                                        MediaList.setPhoneSound(nodeCard.deviceId, nextOn)
                                        page.showFeedback(nextOn
                                            ? qsTr("Mengalirkan suara %1 ke speaker PC…").arg(nodeCard.name)
                                            : qsTr("Menghentikan aliran suara %1").arg(nodeCard.name))
                                    }
                                }

                                Button {
                                    variant: "tonal"
                                    size: "sm"
                                    text: nodeCard.ringing ? qsTr("Berhenti") : qsTr("Deringkan")
                                    iconPath: Icons.ring
                                    enabled: nodeCard.online
                                    onClicked: AppController.ring(nodeCard.deviceId, !nodeCard.ringing)
                                }

                                Button {
                                    variant: "text"
                                    size: "sm"
                                    text: qsTr("Detail")
                                    onClicked: page.openDeviceRequested(nodeCard.index)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
