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

---

## 2. Aşama – Diskte Mesaj Saklama (Bitti ✅)

**Amaç:** Disk IO, buffered/unbuffered fikrine giriş.

* [x] `messages/` klasöründe her mesajı **ayrı dosyada** tut:

  * Örn. `messages/42.msg` içinde sadece o mesajın içeriği
* [x] `SET <id> <msg>`: Diskte dosya oluştur / üzerine yaz
* [x] `GET <id>`: İlgili dosyayı aç, içeriği oku, istemciye dön
* [x] İki farklı IO modu araştırılabilir:

  * [x] **Buffered IO** ile yaz/oku (örn. `BufferedWriter`, `BufferedReader`)
  * [x] **Unbuffered IO** (doğrudan `FileOutputStream`, `FileInputStream`)


  * Buffered vs unbuffered farkı nedir, hangi durumda daha avantajlıdır?

  * 2.Aşamada zaman kaybetmemek için tipik dosyaya yazma işlemi ile bitirip, daha sonra buraya dönebilirsiniz.

### Görev Dağılımı

* **Habib**
  * [x] SET komutu için disk yazma (write) mantığının uygulanması

* **Abdullah**
  * [x] GET komutu için disk okuma (read) mantığının uygulanması

* **Haris**
  * [x] Buffered ve unbuffered disk IO yaklaşımlarının araştırılması

* **Rasha**
  * [x] Disk IO entegrasyonu ve Aşama 2 dokümantasyonunun hazırlanması

### Buffered vs Unbuffered Temel Farkları

**Unbuffered (buffersız) I/O**, veriyi kaynaktan doğrudan okur veya hedefe doğrudan yazar.  
Her `read` veya `write` çağrısı işletim sistemine gider. Bu nedenle çok sayıda küçük
okuma/yazma işlemi yapıldığında performans düşer.  
`FileInputStream` ve `FileReader` gibi sınıflar buffersız çalışır.

**Avantajlar**
- Yapısı basittir, anlaması kolaydır  
- Ek buffer belleği kullanmaz  
- Az sayıda I/O işlemi için yeterlidir  

**Dezavantajlar**
- Performansı düşüktür  
- Her okuma/yazmada işletim sistemine gider  
- Büyük dosyalarda ve sık I/O’da yavaş çalışır


**Buffered (tamponlu) I/O** ise veriyi önce bellekte bir **buffer** içine alır.
Program veriyi bu bellek alanından okur veya bu alana yazar; buffer dolunca ya da
boşalınca işletim sistemiyle iletişime geçilir. Bu yaklaşım, I/O çağrılarını azalttığı
için daha hızlıdır.  
`BufferedInputStream` ve `BufferedReader` bu yapıya örnektir.

**Avantajlar**
- Daha hızlıdır  
- İşletim sistemi çağrıları azalır  
- Büyük dosyalar ve sık I/O işlemleri için idealdir  

**Dezavantajlar**
- Bir miktar ekstra bellek kullanır  
- Yapısı biraz daha karmaşıktır  
- Gerekli durumlarda `flush()` çağrısı gerekebilir  

### Test Kanıtları

![Test 4 – Disk IO ve SET/GET Çalışma Kanıtı](images/test4.png)

#### Loglar

```client outputs
G
ERROR
GET 23
GOAT CR7
GET 88
Ronaldo
GET 99
NOT_FOUND
SET 12 MESSI
OK
GET 12
MESSI
```

```terminal outputs
Node started on 127.0.0.1:5555
Leader listening for text on TCP 127.0.0.1:6666
New TCP client connected: /127.0.0.1:52061
TCP> G  =>  ERROR
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-18T18:50:36.743612700
Members:
 - 127.0.0.1:5555 (me)
======================================
TCP> GET 23  =>  GOAT CR7
TCP> GET 88  =>  Ronaldo
TCP> GET 99  =>  NOT_FOUND
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-18T18:50:46.661575100
Members:
 - 127.0.0.1:5555 (me)
======================================
TCP> SET 12 MESSI  =>  OK
TCP> GET 12  =>  MESSI
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-18T18:50:56.674839300
Members:
 - 127.0.0.1:5555 (me)
======================================
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-18T18:51:06.671870700
Members:
 - 127.0.0.1:5555 (me)
======================================
```



