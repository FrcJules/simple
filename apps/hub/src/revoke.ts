import { DeviceRegistry } from "./devices";

// `npm run devices -w @presence/hub` lists the paired phones; `npm run revoke -w @presence/hub -- <id>` forgets one, `-- --all` forgets all.
const devices = new DeviceRegistry(process.env.PRESENCE_DATA_DIR ?? "./data");
const arg = process.argv[2];

if (arg === "--all") {
  devices.revokeAll();
  console.log("Tous les téléphones sont oubliés : ils devront être appairés de nouveau.");
} else if (arg) {
  if (devices.revoke(arg)) console.log(`Téléphone ${arg} oublié.`);
  else {
    console.error(`Aucun téléphone (ou plusieurs) ne correspond à « ${arg} ». Lancez « npm run devices -w @presence/hub » pour voir la liste.`);
    process.exit(1);
  }
} else {
  const list = devices.devices();
  if (list.length === 0) console.log("Aucun téléphone appairé.");
  for (const d of list) console.log(`${d.id}  ${d.label ?? "(sans nom)"}  appairé le ${new Date(d.createdAt).toLocaleString("fr-FR")}, vu le ${new Date(d.lastUsedAt).toLocaleString("fr-FR")}`);
}
