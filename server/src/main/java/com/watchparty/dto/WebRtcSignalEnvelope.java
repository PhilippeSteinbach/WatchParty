package com.watchparty.dto;

import org.springframework.lang.NonNull;

/**
 * Envelope that wraps a WebRTC signaling payload delivered to a specific peer.
 * The {@code fromConnectionId} identifies the sender so the receiver can
 * associate the signal with the correct RTCPeerConnection.
 */
public record WebRtcSignalEnvelope(
        String type,
        String fromConnectionId,
        String sdp,
        String candidate,
        String sdpMid,
        Integer sdpMLineIndex
) {

    @NonNull
    public static WebRtcSignalEnvelope offer(String fromConnectionId, String sdp) {
        return new WebRtcSignalEnvelope("offer", fromConnectionId, sdp, null, null, null);
    }

    @NonNull
    public static WebRtcSignalEnvelope answer(String fromConnectionId, String sdp) {
        return new WebRtcSignalEnvelope("answer", fromConnectionId, sdp, null, null, null);
    }

    @NonNull
    public static WebRtcSignalEnvelope iceCandidate(String fromConnectionId, String candidate,
                                                      String sdpMid, Integer sdpMLineIndex) {
        return new WebRtcSignalEnvelope("ice-candidate", fromConnectionId, null, candidate, sdpMid, sdpMLineIndex);
    }
}
