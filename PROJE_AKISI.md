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

![Server Logları](images/test3.jpeg)
