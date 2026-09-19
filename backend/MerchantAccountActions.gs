/*
 * Tambahkan dua baris ini di doPost(e), setelah `const action = ...`:
 *
 * const merchantAction = tryHandleMerchantAction_(action, data);
 * if (merchantAction) return merchantAction;
 *
 * File ini memakai kolom J:M di sheet Daftar Pedagang:
 * username, password_hash, session_hash, session_expires.
 */

const MERCHANT_ACCOUNT_COLUMNS_ = {
  username: 10,
  passwordHash: 11,
  sessionHash: 12,
  sessionExpires: 13
};

function tryHandleMerchantAction_(action, data) {
  if (action === "merchant_login") {
    return merchantLogin_(data);
  }
  if (action === "update_my_store") {
    return updateMyStore_(data);
  }
  return null;
}

function merchantLogin_(data) {
  const username = normalizeMerchantUsername_(data.username);
  const password = String(data.password || "");
  if (!username || !password) {
    return jsonResponse(false, "Username dan password wajib diisi.");
  }

  const sheet = getSheet();
  const seller = findMerchantByUsername_(sheet, username);
  if (!seller) {
    return jsonResponse(false, "Username atau password tidak sesuai.");
  }
  if (String(seller.status || "").toLowerCase() !== "approved") {
    return jsonResponse(false, "Toko ini belum aktif.");
  }
  if (seller.passwordHash !== hashMerchantSecret_(username + ":" + password)) {
    return jsonResponse(false, "Username atau password tidak sesuai.");
  }

  const token = Utilities.getUuid() + Utilities.getUuid();
  const expires = new Date(Date.now() + 30 * 24 * 60 * 60 * 1000);
  sheet.getRange(seller.rowNumber, MERCHANT_ACCOUNT_COLUMNS_.sessionHash)
    .setValue(hashMerchantSecret_(token));
  sheet.getRange(seller.rowNumber, MERCHANT_ACCOUNT_COLUMNS_.sessionExpires)
    .setValue(expires);

  return jsonResponse(true, "Login berhasil.", {
    username: seller.username,
    session_token: token,
    nama_toko: seller.namaToko,
    nomor_wa: seller.nomorWa,
    kategori: seller.kategori,
    deskripsi: seller.deskripsi
  });
}

function updateMyStore_(data) {
  const seller = findMerchantBySession_(getSheet(), String(data.session_token || ""));
  if (!seller) {
    return jsonResponse(false, "Sesi login habis. Silakan login kembali.");
  }

  const inputItems = Array.isArray(data.items) ? data.items : [];
  if (inputItems.length === 0 || inputItems.length > 5) {
    return jsonResponse(false, "Isi minimal satu dan maksimal lima barang.");
  }

  const items = [];
  for (let i = 0; i < inputItems.length; i++) {
    const item = inputItems[i] || {};
    const name = String(item.nama_barang || "").trim();
    const price = String(item.harga || "").replace(/\D/g, "");
    if (!name || !price) {
      return jsonResponse(false, "Setiap barang harus memiliki nama dan harga.");
    }
    items.push(name + " - Rp " + formatMerchantPrice_(price));
  }

  const description = items.join("\n");
  seller.sheet.getRange(seller.rowNumber, 5).setValue(description);
  return jsonResponse(true, "Daftar barang berhasil diperbarui.", {
    nama_toko: seller.namaToko,
    deskripsi: description
  });
}

function findMerchantByUsername_(sheet, username) {
  const values = sheet.getDataRange().getValues();
  for (let index = 1; index < values.length; index++) {
    const row = values[index];
    if (normalizeMerchantUsername_(row[9]) === username) {
      return merchantFromRow_(sheet, index + 1, row);
    }
  }
  return null;
}

function findMerchantBySession_(sheet, token) {
  if (!token) return null;
  const tokenHash = hashMerchantSecret_(token);
  const now = Date.now();
  const values = sheet.getDataRange().getValues();
  for (let index = 1; index < values.length; index++) {
    const row = values[index];
    const expires = row[12] instanceof Date ? row[12].getTime() : 0;
    if (String(row[11] || "") === tokenHash && expires > now) {
      return merchantFromRow_(sheet, index + 1, row);
    }
  }
  return null;
}

function merchantFromRow_(sheet, rowNumber, row) {
  return {
    sheet: sheet,
    rowNumber: rowNumber,
    namaToko: String(row[1] || ""),
    nomorWa: String(row[2] || ""),
    kategori: String(row[3] || ""),
    deskripsi: String(row[4] || ""),
    status: String(row[8] || ""),
    username: normalizeMerchantUsername_(row[9]),
    passwordHash: String(row[10] || "")
  };
}

function normalizeMerchantUsername_(value) {
  return String(value || "").trim().toLowerCase().replace(/\s+/g, "");
}

function hashMerchantSecret_(value) {
  const bytes = Utilities.computeDigest(
    Utilities.DigestAlgorithm.SHA_256,
    String(value),
    Utilities.Charset.UTF_8
  );
  return Utilities.base64Encode(bytes);
}

function formatMerchantPrice_(value) {
  return String(value).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
}
