const signalingUrl = "ws://localhost:8080/signal";
const stunConfig = {
    iceServers: [{ urls: "stun:stun.l.google.com:19302" }]
};

const MSG_TYPE = {
    REGISTER:     "register",
    RECONNECT:    "reconnect",
    ID_ASSIGNED:  "id_assigned",
    OFFER:        "offer",
    ANSWER:       "answer",
    NETWORK_PATH: "network_path",
    HANGUP:       "hangup",
    REJECT:       "reject",
    PING:         "ping",
    PONG:         "pong",
    ERROR:        "error"
};

function getPersistentId() {
    let id = localStorage.getItem('webrtc_user_id');
    if (!id) {
        id = "user_" + Math.random().toString(36).substr(2, 9);
        localStorage.setItem('webrtc_user_id', id);
    }
    return id;
}

const myId = getPersistentId();
const socket = new WebSocket(signalingUrl);

let localStream;
let peerConnection;
let currentTargetId = null;
let iceCandidatesQueue = [];

const localVideo = document.getElementById("localVideo");
const remoteVideo = document.getElementById("remoteVideo");
const myIdDisplay = document.getElementById("myIdDisplay");
const targetInput = document.getElementById("targetIdInput");
const callBtn = document.getElementById("callBtn");
const hangupBtn = document.getElementById("hangupBtn");

myIdDisplay.innerText = "Ваш ID: " + myId;

socket.onopen = () => {
    console.log("WebSocket connected");
    sendSignal(MSG_TYPE.REGISTER, null, null);
    startPingPong();
};

socket.onmessage = async (event) => {
    const msg = JSON.parse(event.data);

    switch (msg.messageType) {
        case MSG_TYPE.ID_ASSIGNED:
            console.log("Registered on server as " + msg.messageData);
            break;

        case MSG_TYPE.RECONNECT:
            const partnerId = msg.messageData;
            console.log("Restoring call with " + partnerId);
            targetInput.value = partnerId;
            setTimeout(() => startCall(), 500);
            break;

        case MSG_TYPE.OFFER:
            await handleOffer(msg);
            break;

        case MSG_TYPE.ANSWER:
            await handleAnswer(msg);
            break;

        case MSG_TYPE.NETWORK_PATH:
            await handleNetworkPath(msg);
            break;

        case MSG_TYPE.HANGUP:
            console.log("Companion hanged up the call");
            handleRemoteHangup();
            break;

        case MSG_TYPE.REJECT:
            alert("Call rejected");
            cleanupConnection();
            break;

        case MSG_TYPE.PONG:
            break;
    }
};

function sendSignal(type, receiverId, data) {
    if (socket.readyState === WebSocket.OPEN) {
        const message = {
            messageType: type,
            messageSender: myId,
            messageReceiver: receiverId,
            messageData: data
        };
        socket.send(JSON.stringify(message));
    }
}

function startPingPong() {
    setInterval(() => sendSignal(MSG_TYPE.PING, null, null), 30000);
}


async function startLocalVideo() {
    try {
        if (!localStream) {
            localStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true });
            localVideo.srcObject = localStream;
        }
    } catch (err) {
        console.error("Camera error:", err);
        alert("No access to camera");
    }
}

function createPeerConnection(targetId) {
    currentTargetId = targetId;
    iceCandidatesQueue = [];
    const pc = new RTCPeerConnection(stunConfig);
    pc.onicecandidate = (event) => {
        if (event.candidate) {
            sendSignal(MSG_TYPE.NETWORK_PATH, targetId, event.candidate);
        }
    };

    pc.ontrack = (event) => {
        remoteVideo.srcObject = event.streams[0];
    };

    pc.oniceconnectionstatechange = () => {
        if (pc.iceConnectionState === 'disconnected') {
            console.log("Companion disconnected.");
        }
        if (pc.iceConnectionState === 'failed' || pc.iceConnectionState === 'closed') {
            cleanupConnection();
        }
    };

    if (localStream) {
        localStream.getTracks().forEach(track => pc.addTrack(track, localStream));
    }

    return pc;
}

async function startCall() {
    const targetId = targetInput.value;
    if (!targetId || targetId === myId) return;
    await startLocalVideo();
    peerConnection = createPeerConnection(targetId);

    const offer = await peerConnection.createOffer();
    await peerConnection.setLocalDescription(offer);

    sendSignal(MSG_TYPE.OFFER, targetId, offer);
    toggleButtons(true);
}

async function handleOffer(msg) {
    const callerId = msg.messageSender;
    if (peerConnection && currentTargetId === callerId) {
        peerConnection.close();
    }
    await startLocalVideo();
    peerConnection = createPeerConnection(callerId);

    await peerConnection.setRemoteDescription(new RTCSessionDescription(msg.messageData));

    await processIceQueue();

    const answer = await peerConnection.createAnswer();
    await peerConnection.setLocalDescription(answer);

    sendSignal(MSG_TYPE.ANSWER, callerId, answer);
    toggleButtons(true);
}

async function handleAnswer(msg) {
    if (!peerConnection) return;
    await peerConnection.setRemoteDescription(new RTCSessionDescription(msg.messageData));
    await processIceQueue();
}

async function handleNetworkPath(msg) {
    const candidate = new RTCIceCandidate(msg.messageData);
    if (peerConnection && peerConnection.remoteDescription && peerConnection.remoteDescription.type) {
        try {
            await peerConnection.addIceCandidate(candidate);
        } catch (e) { console.error(e); }
    } else {
        iceCandidatesQueue.push(candidate);
    }
}

async function processIceQueue() {
    while(iceCandidatesQueue.length > 0) {
        const c = iceCandidatesQueue.shift();
        try { await peerConnection.addIceCandidate(c); } catch(e) {}
    }
}

function hangupCall() {
    if (currentTargetId) {
        sendSignal(MSG_TYPE.HANGUP, currentTargetId, null);
    }
    cleanupConnection();
}

function handleRemoteHangup() {
    alert("Call ended");
    cleanupConnection();
}

function cleanupConnection() {
    if (peerConnection) {
        peerConnection.close();
        peerConnection = null;
    }
    remoteVideo.srcObject = null;
    currentTargetId = null;
    toggleButtons(false);
}

function toggleButtons(isCalling) {
    callBtn.style.display = isCalling ? 'none' : 'inline-block';
    hangupBtn.style.display = isCalling ? 'inline-block' : 'none';
}

callBtn.onclick = startCall;
hangupBtn.onclick = hangupCall;
hangupBtn.style.display = 'none';
