import { useEffect, useMemo, useState } from "react";
import { qrPath, startPairing, type PairingOffer } from "./pairing";

type State = { kind: "loading" } | { kind: "failed" } | { kind: "ready"; offer: PairingOffer };

/**
 * Full-screen QR code for the family to add a phone. Shown only after someone touched the clock on purpose; closes itself when the
 * one-time code expires. Being in front of this screen replaces typing anything: the fingerprint is shown beside the code to compare.
 */
export function PairingScreen({ onClose }: { onClose: () => void }) {
  const [state, setState] = useState<State>({ kind: "loading" });
  const [left, setLeft] = useState(0);

  useEffect(() => {
    let alive = true;
    void startPairing().then((offer) => {
      if (!alive) return;
      if (offer) {
        setState({ kind: "ready", offer });
        setLeft(offer.expiresInSeconds);
      } else setState({ kind: "failed" });
    });
    return () => { alive = false; };
  }, []);

  useEffect(() => {
    if (state.kind !== "ready") return;
    const t = setInterval(() => setLeft((s) => s - 1), 1000);
    return () => clearInterval(t);
  }, [state.kind]);

  useEffect(() => {
    if (state.kind === "ready" && left <= 0) onClose();
  }, [state.kind, left, onClose]);

  const qr = useMemo(() => (state.kind === "ready" ? qrPath(state.offer.link) : null), [state]);

  return (
    <main className="screen pairing">
      <h1>Ajouter un téléphone</h1>
      {state.kind === "loading" && <p className="body">Un instant…</p>}
      {state.kind === "failed" && <p className="body">Impossible de préparer le code. Vérifiez que l'écran est connecté au Wi-Fi de la maison.</p>}
      {state.kind === "ready" && qr && (
        <>
          <svg className="qr" viewBox={`-4 -4 ${qr.size + 8} ${qr.size + 8}`} role="img" aria-label="QR code d'appairage" shapeRendering="crispEdges">
            <rect x="-4" y="-4" width={qr.size + 8} height={qr.size + 8} fill="#fff" />
            <path d={qr.d} fill="#000" />
          </svg>
          <p className="body">Scannez avec l'appareil photo du téléphone, puis comparez l'empreinte :</p>
          <p className="fingerprint">{state.offer.fingerprint}</p>
          <p className="ago">Ce code ne marche qu'une fois, encore {Math.max(left, 0)} secondes.</p>
        </>
      )}
      <button className="close" onClick={onClose}>Fermer</button>
    </main>
  );
}
