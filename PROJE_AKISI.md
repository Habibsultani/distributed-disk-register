## 1. Aşama – TCP SET / GET (Bitti ✅)

* [x] Grup üyeleri girişini yap.
* [x] GitHub’daki şablon repoyu **fork** et.
* [x] GitHub’da ekip için proje oluştur, task’ları tanımla ve üyelere ata.
* [x] TCP server için SET / GET komutlarını parse eden yapı geliştir.

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

* [x] `messages/` klasöründe her mesajı **ayrı dosyada** tut
* [x] `SET <id> <msg>`: Diskte dosya oluştur / üzerine yaz
* [x] `GET <id>`: İlgili dosyayı aç, içeriği oku, istemciye dön
* [x] İki farklı IO modu araştırılabilir:

  * [x] **Buffered IO** ile yaz/oku (örn. `BufferedWriter`, `BufferedReader`)
  * [x] **Unbuffered IO** (doğrudan `FileOutputStream`, `FileInputStream`)


  * Buffered vs unbuffered farkı nedir, hangi durumda daha avantajlıdır?

  * 2.Aşamada zaman kaybetmemek için tipik dosyaya yazma işlemi ile bitirip, daha sonra buraya dönebilirsiniz.

### Görev Dağılımı

* **Haris**
  * [x] SET komutu için disk yazma (write) mantığının uygulanması

* **Habib**
  * [x] GET komutu için disk okuma (read) mantığının uygulanması

* **Abdullah**
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

![Test 1 – Disk IO ve SET/GET Çalışma Kanıtı](images/test4.png)

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

---

## 3. Aşama – gRPC Mesaj Modeli (Bitti ✅)

**Amaç:**

* [x] `.proto` dosyasında StoredMessage tanımı eklenmeli.
* [x] Java tarafında mesaj temsilini Protobuf (StoredMessage, MessageId, StoreResult) ile kullanılmalı.
* [x] gRPC servis iskeleti oluşturuldu: StorageService { Store(StoredMessage) returns (StoreResult); Retrieve(MessageId) returns (StoredMessage) }.
* [x] Henüz dağıtık replika yok; amaç gRPC fonksiyonunu ayağa kaldırmak.

### Görev Dağılımı

* **Abdullah**
  * [x] Storage protobuf mesajları (StoredMessage, MessageId, StoreResult)

* **Rasha**
  * [x] StorageService gRPC arayüzü (Store, Retrieve RPC)

* **Habib**
  * [x] StorageService server iskeleti (StorageServiceImpl, disk-backed)

* **Haris**
  * [x] gRPC storage testi ve dokümantasyon (Stage 3)

### Kod/Proto Durumu

* `family.proto`: StoredMessage, MessageId, StoreResult + StorageService { Store, Retrieve } (tek node için)
* `StorageServiceImpl`: `Store` RPC dosyaya (`messages/<id>.msg`) ve in-memory store'a yazar, `Retrieve` RPC dosyadan okur/yoksa NOT_FOUND.
* `NodeMain`: gRPC server'a StorageService ekli (FamilyService ile birlikte).

### Testler (tek node, grpcurl)

* Store:
  ```bash
  grpcurl -plaintext -proto src/main/proto/family.proto \
    -d '{"id":42,"text":"hello from curl"}' 127.0.0.1:5556 family.StorageService/Store
  ```
  Beklenen: `{"ok":true}` ve `messages/42.msg` oluşur.

* Retrieve:
  ```bash
  grpcurl -plaintext -proto src/main/proto/family.proto \
    -d '{"id":42}' 127.0.0.1:5556 family.StorageService/Retrieve
  ```
  Beklenen: `{"id":42,"text":"hello from curl"}`.

* NOT_FOUND:
  ```bash
  grpcurl -plaintext -proto src/main/proto/family.proto \
    -d '{"id":9999}' 127.0.0.1:5556 family.StorageService/Retrieve
  ```
  Beklenen: gRPC `NOT_FOUND`.

### Notlar

* Bu aşamada dağıtık replika yok; tolerans/replication Stage 4+ için beklemede.

---

## 4. Aşama – Tolerance=1 ve 2 için Dağıtık Kayıt (Bitti ✅)

**Amaç:** Hata toleransı 1 ve 2 için **temel dağıtık kayıt sistemi**.

* [x] `tolerance.conf` dosyasını okuyun:

  * İçinde tek satır olsun: `TOLERANCE=2`
* [x] Lider, her SET isteğinde:

  1. Gelen id+mesajı diske kaydetsin (kendi mesaj haritasına da eklesin)
  2. Üye listesinden tolerance sayısı kadar üye seçsin:

     * Tolerance=1 → 1 üye
     * Tolerance=2 → 2 üye
  3. Bu üyelere gRPC ile `Store(StoredMessage)` RPC’si göndersin
  4. Hepsinden başarılı yanıt geldiyse istemciye `OK`
  5. Bir veya daha fazlası başarısız olursa:

     * Bu durumda ne yapılacağı (retry, ERROR, vb) takım tasarımına bırakılabilir
* [x] Lider, “mesaj id → hangi üyelerde var” bilgisini bir map’te tutsun:

  * `Map<Integer, List<MemberId>>`
* [x] GET isteğinde:

  * Eğer liderin kendi diskinde varsa doğrudan kendinden okusun
  * Yoksa mesajın tutulduğu üye listesinden sırayla gRPC ile `Retrieve` isteği göndersin
  * İlk cevap veren (ya da hayatta kalan) üyeden mesajı alıp istemciye döndürsün

### Görev Dağılımı

* **Habib**
  * [x] tolerance.conf okuyucusunu kodla.

* **Haris**
  * [x] 1 ve 2 tolerans seviyeleri için replika seçimini gerçekleştir.

* **Abdullah**
  * [x] Lider (leader) düğüm üzerinde mesaj-üye eşleşmesini takip et.

* **Rasha**
  * [x] Dağıtık GET mantığını gerçekleştir.

### Testler ve Test Kanıtları

#### Test Senaryosu 1
* TOLERANCE=2
* başarılı SET + mapping + GET diskten
* SET 200 hello yap -> GET 200 yap
![Test Senaryosu 1 Çalışma Kanıtı](images/test5.png)

##### Loglar

```client outputs
SET 200 hello
OK
GET 200
hello
```

```terminal outputs
Node started on 127.0.0.1:5555
Configured tolerance level: 2
Leader listening for text on TCP 127.0.0.1:6666
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T15:59:05.195287600
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
======================================
New TCP client connected: /127.0.0.1:58061
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T15:59:15.195148400
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
 - 127.0.0.1:5557
======================================
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T15:59:25.195331
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
 - 127.0.0.1:5557
======================================
[REPL] Store OK on 127.0.0.1:5556
[REPL] Store OK on 127.0.0.1:5557
[MAPPING] id=200 -> 127.0.0.1:5555, 127.0.0.1:5556, 127.0.0.1:5557
TCP> SET 200 hello  =>  OK
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T15:59:35.197869500
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
 - 127.0.0.1:5557
======================================
[GET] Local disk hit id=200
TCP> GET 200  =>  hello
```

#### Test Senaryosu 2
* TOLERANCE=2
* leader’da yok -> üyeden Retrieve ile getir
* SET 200 hello yap -> GET 200 yap -> 200.msg sil -> GET 200 yap
![Test Senaryosu 2 Çalışma Kanıtı](images/test6.png)

##### Loglar

```client outputs
SET 200 hello
OK
GET 200
hello
GET 200
hello
```

```terminal outputs
Node started on 127.0.0.1:5555
Configured tolerance level: 2
Leader listening for text on TCP 127.0.0.1:6666
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:08:43.455836500
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
======================================
New TCP client connected: /127.0.0.1:52084
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:08:53.460467300
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
 - 127.0.0.1:5557
======================================
[REPL] Store OK on 127.0.0.1:5556
[REPL] Store OK on 127.0.0.1:5557
[MAPPING] id=200 -> 127.0.0.1:5555, 127.0.0.1:5556, 127.0.0.1:5557
TCP> SET 200 hello  =>  OK
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:09:03.449003100
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
 - 127.0.0.1:5557
======================================
[GET] Local disk hit id=200
TCP> GET 200  =>  hello
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:09:13.449610300
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
 - 127.0.0.1:5557
======================================
[GET] Local disk miss id=200, trying members...
[GET] Retrieved from 127.0.0.1:5556
TCP> GET 200  =>  hello
```

#### Test Senaryosu 3
* TOLERANCE=2
* 1 üye down olsa bile GET çalışıyor
* SET 200 hello yap -> 200.msg sil -> Üyelerden birini kapat -> GET 200 yap
![Test Senaryosu 3 Çalışma Kanıtı](images/test7.png)

##### Loglar

```client outputs
SET 200 hello
OK
GET 200
hello
```

```terminal outputs
Node started on 127.0.0.1:5555
Configured tolerance level: 2
Leader listening for text on TCP 127.0.0.1:6666
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:18:10.262213800
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
======================================
New TCP client connected: /127.0.0.1:50749
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:18:20.254065100
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
 - 127.0.0.1:5557
======================================
[REPL] Store OK on 127.0.0.1:5556
[REPL] Store OK on 127.0.0.1:5557
[MAPPING] id=200 -> 127.0.0.1:5555, 127.0.0.1:5556, 127.0.0.1:5557
TCP> SET 200 hello  =>  OK
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:18:30.247867100
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
 - 127.0.0.1:5557
======================================
Node 127.0.0.1:5556 unreachable, removing from family
[GET] Local disk miss id=200, trying members...
[GET] Retrieved from 127.0.0.1:5557
TCP> GET 200  =>  hello
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:18:50.247708400
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5557
```

#### Test Senaryosu 4
* TOLERANCE=2
* SET fail path -> ERROR
* Üyelerden birini kapat -> SET 200 hello yap
![Test Senaryosu 4 Çalışma Kanıtı](images/test8.png)

##### Loglar

```client outputs
SET 200 hello
ERROR
```

```terminal outputs
Node started on 127.0.0.1:5555
Configured tolerance level: 2
Leader listening for text on TCP 127.0.0.1:6666
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:26:32.174110300
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
======================================
New TCP client connected: /127.0.0.1:50595
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:26:42.167686100
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
 - 127.0.0.1:5557
======================================
Node 127.0.0.1:5556 unreachable, removing from family
[REPL] Not enough members. need=2 available=1
TCP> SET 200 hello  =>  ERROR
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:27:02.169732
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5557
======================================
```

#### Test Senaryosu 5
* TOLERANCE=1
* 1 üyeye yazıyor
* Leader + 2 üye çalıştır -> SET 200 hello yap
![Test Senaryosu 5 Çalışma Kanıtı](images/test9.png)

##### Loglar

```client outputs
SET 200 hello
OK
```

```terminal outputs
Node started on 127.0.0.1:5555
Configured tolerance level: 1
Leader listening for text on TCP 127.0.0.1:6666
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:31:38.636905800
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
======================================
New TCP client connected: /127.0.0.1:60696
======================================
Family at 127.0.0.1:5555 (me)
Time: 2025-12-19T16:31:48.628418700
Members:
 - 127.0.0.1:5555 (me)
 - 127.0.0.1:5556
 - 127.0.0.1:5557
======================================
[REPL] Store OK on 127.0.0.1:5556
[MAPPING] id=200 -> 127.0.0.1:5555, 127.0.0.1:5556
TCP> SET 200 hello  =>  OK
```

---


