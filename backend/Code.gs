const SPREADSHEET_ID = "1-BIgVF3yNnOsffw_3Lw8cdi97QAG9AEZt4aM2SFcxTQ";
const SELLER_SHEET_NAME = "Daftar Pedagang";
const PROMO_SHEET_NAME = "Promo";
const REPORT_SHEET_NAME = "Laporan";
const DRIVE_FOLDER_ID = "1BnfLiGTXBWK_-IAoHR6e2fHtCbnvxKG1";
const SELLER_COLUMNS = {
  timestamp: 1, nama: 2, wa: 3, kategori: 4, deskripsi: 5, lokasi: 6,
  fotoUrl: 7, fotoId: 8, status: 9, username: 10, passwordHash: 11,
  sessionHash: 12, sessionExpires: 13
};

function doGet() {
  return jsonResponse_(true, "Pasarku Sidokerto backend aktif.");
}

function doPost(e) {
  try {
    const data = JSON.parse((e.postData && e.postData.contents) || "{}");
    const action = String(data.action || "register_seller");
    if (action === "register_seller") return registerSeller_(data);
    if (action === "check_seller") return checkSeller_(data);
    if (action === "get_sellers") return getSellers_();
    if (action === "merchant_login") return merchantLogin_(data);
    if (action === "update_my_store") return updateMyStore_(data);
    if (action === "post_promo") return postPromo_(data);
    if (action === "get_promos") return getPromos_();
    if (action === "report_item") return reportItem_(data);
    return jsonResponse_(false, "Action tidak dikenal: " + action);
  } catch (error) {
    return jsonResponse_(false, "ERROR: " + error.toString());
  }
}

function registerSeller_(data) {
  const namaToko = String(data.nama_toko || "").trim();
  const nomorWa = normalizePhone_(data.nomor_wa);
  const kategori = String(data.kategori || "").trim();
  const deskripsi = String(data.deskripsi || "").trim();
  const lokasi = String(data.lokasi || "").trim();
  const fotoBase64 = String(data.foto_base64 || "").trim();
  const filename = String(data.foto_filename || makeSafeFilename_(namaToko));
  if (!namaToko || !nomorWa || !kategori || !deskripsi || !fotoBase64) {
    return jsonResponse_(false, "Data wajib belum lengkap.");
  }
  if (nomorWa.length < 10) return jsonResponse_(false, "Nomor WhatsApp tidak valid.");

  const sheet = getSellerSheet_();
  if (findSellerByPhone_(sheet, nomorWa)) {
    return jsonResponse_(false, "Nomor WhatsApp ini sudah terdaftar sebagai pedagang.");
  }
  const image = saveBase64Image_(fotoBase64, filename);
  const username = nextMerchantUsername_(sheet);
  sheet.appendRow([
    new Date(), namaToko, nomorWa, kategori, deskripsi, lokasi,
    image.url, image.id, "pending", username,
    hashMerchantSecret_(username + ":admin"), "", ""
  ]);
  return jsonResponse_(true, "Pendaftaran berhasil dikirim.", {
    nama_toko: namaToko, nomor_wa: nomorWa, kategori: kategori, status: "pending"
  });
}

function checkSeller_(data) {
  const nomorWa = normalizePhone_(data.nomor_wa);
  if (!nomorWa || nomorWa.length < 10) return jsonResponse_(false, "Nomor WhatsApp tidak valid.");
  const seller = findSellerByPhone_(getSellerSheet_(), nomorWa);
  return seller
    ? jsonResponse_(true, "Pedagang ditemukan.", sellerToResponse_(seller))
    : jsonResponse_(false, "Nomor ini belum terdaftar sebagai pedagang.");
}

function getSellers_() {
  const values = getSellerSheet_().getDataRange().getValues();
  const sellers = [];
  for (let index = 1; index < values.length; index++) {
    const seller = sellerFromRow_(getSellerSheet_(), index + 1, values[index]);
    if (seller.status.toLowerCase() === "approved") sellers.push(sellerToResponse_(seller));
  }
  return jsonResponse_(true, "Data pedagang berhasil diambil.", { sellers: sellers });
}

function merchantLogin_(data) {
  const username = normalizeMerchantUsername_(data.username);
  const password = String(data.password || "");
  if (!username || !password) return jsonResponse_(false, "Username dan password wajib diisi.");
  const seller = findMerchantByUsername_(getSellerSheet_(), username);
  if (!seller || seller.status.toLowerCase() !== "approved") {
    return jsonResponse_(false, "Username atau password tidak sesuai.");
  }
  if (seller.passwordHash !== hashMerchantSecret_(username + ":" + password)) {
    return jsonResponse_(false, "Username atau password tidak sesuai.");
  }
  const token = Utilities.getUuid() + Utilities.getUuid();
  seller.sheet.getRange(seller.rowNumber, SELLER_COLUMNS.sessionHash).setValue(hashMerchantSecret_(token));
  seller.sheet.getRange(seller.rowNumber, SELLER_COLUMNS.sessionExpires)
    .setValue(new Date(Date.now() + 30 * 24 * 60 * 60 * 1000));
  return jsonResponse_(true, "Login berhasil.", Object.assign(sellerToResponse_(seller), {
    username: seller.username, session_token: token
  }));
}

function updateMyStore_(data) {
  const seller = findMerchantBySession_(getSellerSheet_(), String(data.session_token || ""));
  if (!seller) return jsonResponse_(false, "Sesi login habis. Silakan login kembali.");
  const rawItems = Array.isArray(data.items) ? data.items : [];
  if (rawItems.length < 1 || rawItems.length > 5) {
    return jsonResponse_(false, "Isi minimal satu dan maksimal lima barang.");
  }
  const lines = [];
  for (let index = 0; index < rawItems.length; index++) {
    const item = rawItems[index] || {};
    const name = String(item.nama_barang || "").trim();
    const digits = String(item.harga || "").replace(/\D/g, "");
    if (!name || !digits) return jsonResponse_(false, "Setiap barang harus memiliki nama dan harga.");
    lines.push(name + " - Rp " + formatPrice_(digits));
  }
  const deskripsi = lines.join("\n");
  seller.sheet.getRange(seller.rowNumber, SELLER_COLUMNS.deskripsi).setValue(deskripsi);
  return jsonResponse_(true, "Daftar barang berhasil diperbarui.", { nama_toko: seller.namaToko, deskripsi: deskripsi });
}

function postPromo_(data) {
  const namaToko = String(data.nama_toko || "").trim();
  const nomorWa = normalizePhone_(data.nomor_wa);
  const deskripsi = String(data.deskripsi_promo || "").trim();
  const imageBase64 = String(data.promo_image_base64 || "").trim();
  if (!namaToko || !nomorWa || (!deskripsi && !imageBase64)) return jsonResponse_(false, "Data promo belum lengkap.");
  let imageUrl = "";
  if (imageBase64) imageUrl = saveBase64Image_(imageBase64, String(data.promo_image_filename || makeSafeFilename_(namaToko))).url;
  const type = imageUrl ? "image" : "text";
  const sheet = getOrCreateSheet_(PROMO_SHEET_NAME, ["timestamp", "nama_toko", "nomor_wa", "deskripsi_promo", "promo_image_url", "type", "status"]);
  sheet.appendRow([new Date(), namaToko, nomorWa, deskripsi, imageUrl, type, "active"]);
  return jsonResponse_(true, "Promo berhasil dipasang.");
}

function getPromos_() {
  const sheet = getOrCreateSheet_(PROMO_SHEET_NAME, ["timestamp", "nama_toko", "nomor_wa", "deskripsi_promo", "promo_image_url", "type", "status"]);
  const values = sheet.getDataRange().getValues();
  const promos = [];
  const threeHoursAgo = Date.now() - 3 * 60 * 60 * 1000;
  for (let index = 1; index < values.length; index++) {
    const row = values[index];
    const timestamp = row[0] instanceof Date ? row[0].getTime() : 0;
    if (String(row[6] || "active").toLowerCase() !== "active" || timestamp < threeHoursAgo) continue;
    promos.push({
      timestamp: formatDateSafe_(row[0]), nama_toko: String(row[1] || ""), nomor_wa: String(row[2] || ""),
      deskripsi_promo: String(row[3] || ""), promo_image_url: String(row[4] || ""), type: String(row[5] || "text")
    });
  }
  return jsonResponse_(true, "Data promo berhasil diambil.", { promos: promos });
}

function reportItem_(data) {
  const sheet = getOrCreateSheet_(REPORT_SHEET_NAME, ["timestamp", "target_type", "target_nama", "target_wa", "alasan", "detail"]);
  sheet.appendRow([
    new Date(), String(data.target_type || ""), String(data.target_nama || ""),
    String(data.target_wa || ""), String(data.alasan || ""), String(data.detail || "")
  ]);
  return jsonResponse_(true, "Laporan berhasil dikirim. Terima kasih.");
}

function getSellerSheet_() {
  const sheet = SpreadsheetApp.openById(SPREADSHEET_ID).getSheetByName(SELLER_SHEET_NAME);
  if (!sheet) throw new Error("Sheet tidak ditemukan: " + SELLER_SHEET_NAME);
  return sheet;
}

function getOrCreateSheet_(name, headers) {
  const spreadsheet = SpreadsheetApp.openById(SPREADSHEET_ID);
  let sheet = spreadsheet.getSheetByName(name);
  if (!sheet) sheet = spreadsheet.insertSheet(name);
  if (sheet.getLastRow() === 0) sheet.appendRow(headers);
  return sheet;
}

function findSellerByPhone_(sheet, phone) {
  const values = sheet.getDataRange().getValues();
  for (let index = 1; index < values.length; index++) {
    if (normalizePhone_(values[index][2]) === phone) return sellerFromRow_(sheet, index + 1, values[index]);
  }
  return null;
}

function findMerchantByUsername_(sheet, username) {
  const values = sheet.getDataRange().getValues();
  for (let index = 1; index < values.length; index++) {
    if (normalizeMerchantUsername_(values[index][9]) === username) return sellerFromRow_(sheet, index + 1, values[index]);
  }
  return null;
}

function findMerchantBySession_(sheet, token) {
  if (!token) return null;
  const tokenHash = hashMerchantSecret_(token);
  const values = sheet.getDataRange().getValues();
  for (let index = 1; index < values.length; index++) {
    const row = values[index];
    const expires = row[12] instanceof Date ? row[12].getTime() : 0;
    if (String(row[11] || "") === tokenHash && expires > Date.now()) return sellerFromRow_(sheet, index + 1, row);
  }
  return null;
}

function sellerFromRow_(sheet, rowNumber, row) {
  return {
    sheet: sheet, rowNumber: rowNumber, namaToko: String(row[1] || ""), nomorWa: String(row[2] || ""),
    kategori: String(row[3] || ""), deskripsi: String(row[4] || ""), lokasi: String(row[5] || ""),
    fotoUrl: String(row[6] || ""), fotoId: String(row[7] || ""), status: String(row[8] || ""),
    username: normalizeMerchantUsername_(row[9]), passwordHash: String(row[10] || "")
  };
}

function sellerToResponse_(seller) {
  return {
    nama_toko: seller.namaToko, nomor_wa: seller.nomorWa, kategori: seller.kategori,
    deskripsi: seller.deskripsi, lokasi: seller.lokasi, foto_url: seller.fotoUrl,
    foto_file_id: seller.fotoId, status: seller.status
  };
}

function saveBase64Image_(base64, filename) {
  const clean = base64.indexOf(",") >= 0 ? base64.split(",")[1] : base64;
  const file = DriveApp.getFolderById(DRIVE_FOLDER_ID).createFile(
    Utilities.newBlob(Utilities.base64Decode(clean), "image/jpeg", filename)
  );
  return { url: file.getUrl(), id: file.getId() };
}

function nextMerchantUsername_(sheet) {
  const values = sheet.getDataRange().getValues();
  let highest = 0;
  for (let index = 1; index < values.length; index++) {
    const match = normalizeMerchantUsername_(values[index][9]).match(/^pasarku(\d+)$/);
    if (match) highest = Math.max(highest, Number(match[1]));
  }
  return "pasarku" + String(highest + 1).padStart(3, "0");
}

function normalizePhone_(value) {
  let cleaned = String(value || "").replace(/\D/g, "");
  if (cleaned.indexOf("0") === 0) cleaned = "62" + cleaned.substring(1);
  return cleaned;
}

function normalizeMerchantUsername_(value) {
  return String(value || "").trim().toLowerCase().replace(/\s+/g, "");
}

function hashMerchantSecret_(value) {
  const bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(value), Utilities.Charset.UTF_8);
  return Utilities.base64Encode(bytes);
}

function formatPrice_(value) {
  return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
}

function makeSafeFilename_(name) {
  const base = String(name || "foto_pedagang").replace(/[^a-zA-Z0-9-_ ]/g, "").trim().replace(/\s+/g, "_");
  return base + "_" + Utilities.formatDate(new Date(), "Asia/Jakarta", "yyyyMMdd_HHmmss") + ".jpg";
}

function formatDateSafe_(value) {
  return value instanceof Date ? Utilities.formatDate(value, "Asia/Jakarta", "yyyy-MM-dd HH:mm:ss") : String(value || "");
}

function jsonResponse_(success, message, extra) {
  const output = Object.assign({ success: success, message: message }, extra || {});
  return ContentService.createTextOutput(JSON.stringify(output)).setMimeType(ContentService.MimeType.JSON);
}
