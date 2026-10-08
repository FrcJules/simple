import { useCallback, useEffect, useMemo, useState } from "react";
import { cancelPairing, qrPath, startPairing, type PairingOffer } from "./pairing";
import { pairingText as t } from "./strings";

type State = { kind: "loading" } | { kind: "failed" } | { kind: "ready"; offer: PairingOffer };

/** Without an answer the screen goes back on its own: someone who touched the clock by chance is not left in front of an error. */
const GIVE_UP_MS = 30_000;

/**
 * Full-screen QR code for the family to add a phone. Shown only after someone touched the clock on purpose; closes itself when the
 * one-time code expires or when the hub does not answer. The fingerprint is shown beside the code to compare with the phone's.
 */
export function PairingScreen({ onClose: close }: { onClose: () => void }) {
  const onClose = useCallback(() => { void cancelPairing(); close(); }, [close]);
  const [state, setState] = useState<State>({ kind: "loading" });
  const [left, setLeft] = useState(0);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let alive = true;
    setState({ kind: "loading" });
    void startPairing().then((offer) => {
      if (!alive) return;
      if (offer && offer.expiresInSeconds > 0) {
        setState({ kind: "ready", offer });
        setLeft(offer.expiresInSeconds);
      } else setState({ kind: "failed" });
    });
    return () => { alive = false; };
  }, [attempt]);

  useEffect(() => {
    if (state.kind !== "ready") return;
    const timer = setInterval(() => setLeft((s) => s - 1), 1000);
    return () => clearInterval(timer);
  }, [state.kind]);

  useEffect(() => {
    if (state.kind === "ready" && left <= 0) onClose();
  }, [state.kind, left, onClose]);

  useEffect(() => {
    if (state.kind === "ready") return;
    const timer = setTimeout(onClose, GIVE_UP_MS);
    return () => clearTimeout(timer);
  }, [state.kind, attempt, onClose]);

  const qr = useMemo(() => (state.kind === "ready" ? qrPath(state.offer.link) : null), [state]);
  const share = state.kind === "ready" ? Math.max(0, Math.min(1, left / state.offer.expiresInSeconds)) : 0;

  return (
    <main className="screen pairing">
      <h1>{t.title}</h1>
      <p className="reassure">{t.reassurance}</p>
      {state.kind === "loading" && <p className="body">{t.loading}</p>}
      {state.kind === "failed" && (
        <>
          <p className="body">{t.failed}</p>
          <p className="hint">{t.failedDetail}</p>
          <button className="retry" onClick={() => setAttempt((n) => n + 1)}>{t.retry}</button>
        </>
      )}
      {state.kind === "ready" && qr && (
        <>
          <svg className="qr" viewBox={`-4 -4 ${qr.size + 8} ${qr.size + 8}`} role="img" aria-label={t.qrLabel} shapeRendering="crispEdges">
            <rect x="-4" y="-4" width={qr.size + 8} height={qr.size + 8} fill="#fff" />
            <path d={qr.d} fill="#000" />
          </svg>
          <p className="hint">{t.scan}</p>
          <p className="fingerprint">{state.offer.fingerprint}</p>
          <div className="timer" aria-hidden="true"><div style={{ width: `${share * 100}%` }} /></div>
          <p className="hint">{t.expires(Math.max(left, 0))}</p>
        </>
      )}
      <button className="close" onClick={onClose}>✕ {t.close}</button>
    </main>
  );
}
