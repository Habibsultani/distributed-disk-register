## 1. Aşama – TCP SET / GET (Bitti ✅)

* [x] Grup üyeleri girişini yap.
* [x] GitHub’daki şablon repoyu **fork** et.
* [x] GitHub’da ekip için proje oluştur, task’ları tanımla ve üyelere ata.
* [x] TCP server için SET / GET komutlarını parse eden yapı geliştir.
  * Örn: `Command parse(String line)` → `SetCommand` / `GetCommand`

  * `SET` → `map.put(id, msg)` + `OK`
  * `GET` → `map.get(id)` + bulunamazsa `NOT_FOUND`

### Görev Dağılımı

* **Habib**
  * [x] Command abstraction tasarımı  
    (`Command`, `SetCommand`, `GetCommand`)

* **Abdullah**
  * [x] `CommandParser` implementasyonu  
    (SET / GET protokolü)

* **Haris**
  * [x] `CommandParser`’ın TCP server’a entegrasyonu

* **Rasha**
  * [x] TCP SET / GET testleri
  * [x] Aşama 1 dokümantasyonu

### Test Kanıtları

![TCP SET GET Test](images/test1.jpeg)

![Server Logları](images/test2.jpeg)

![Server Logları](images/test3.jpeg)


## 2. Aşama – Diskte Mesaj Saklama

...

### Görev Dağılımı

...

### Buffered vs Unbuffered Temel Farkları

**Unbuffered (buffersız) I/O**, veriyi kaynaktan doğrudan okur veya hedefe doğrudan yazar.  
Her `read` veya `write` çağrısı işletim sistemine gider. Bu nedenle çok sayıda küçük
okuma/yazma işlemi yapıldığında performans düşer.  
`FileInputStream` ve `FileReader` gibi sınıflar buffersız çalışır.

**Buffered (tamponlu) I/O** ise veriyi önce bellekte bir **buffer** içine alır.
Program veriyi bu bellek alanından okur veya bu alana yazar; buffer dolunca ya da
boşalınca işletim sistemiyle iletişime geçilir. Bu yaklaşım, I/O çağrılarını azalttığı
için daha hızlıdır.  
`BufferedInputStream` ve `BufferedReader` bu yapıya örnektir.

---

### Unbuffered (Buffersız) I/O

**Avantajlar**
- Yapısı basittir, anlaması kolaydır  
- Ek buffer belleği kullanmaz  
- Az sayıda I/O işlemi için yeterlidir  

**Dezavantajlar**
- Performansı düşüktür  
- Her okuma/yazmada işletim sistemine gider  
- Büyük dosyalarda ve sık I/O’da yavaş çalışır  

---

### Buffered (Tamponlu) I/O

**Avantajlar**
- Daha hızlıdır  
- İşletim sistemi çağrıları azalır  
- Büyük dosyalar ve sık I/O işlemleri için idealdir  

**Dezavantajlar**
- Bir miktar ekstra bellek kullanır  
- Yapısı biraz daha karmaşıktır  
- Gerekli durumlarda `flush()` çağrısı gerekebilir  

