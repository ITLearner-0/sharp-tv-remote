#!/usr/bin/env python3
"""Fiche Play Store d'OmniZap : écrit metadata/android/<langue>/{title,short_description,full_description}.txt."""
import os

L = {}

L["fr-FR"] = ("OmniZap – Télécommande TV",
"Télécommande universelle : Android TV, Google TV, Samsung, LG, infrarouge.",
"""Transformez votre téléphone en télécommande pour toutes vos TV.

OmniZap pilote votre TV par le Wi-Fi, par l'émetteur infrarouge du téléphone ou en Bluetooth. Pas de compte, pas de publicité : vos réglages restent sur votre téléphone.

📺 COMPATIBLE
• Android TV et Google TV (Sony, TCL, Philips, Sharp, Hisense, Xiaomi, Chromecast avec Google TV…)
• TV Samsung (Tizen, 2016 et après) et LG (webOS) en Wi-Fi
• Toutes les TV à infrarouge, si votre téléphone a un émetteur (nombreux Xiaomi, Redmi, Poco, Huawei, Honor)
• Bluetooth (expérimental) : certaines TV n'acceptent pas les télécommandes venant d'un téléphone

🎮 LA TÉLÉCOMMANDE (GRATUITE)
• Toutes les touches : flèches, OK, retour, accueil, volume, chaînes, lecture, pavé numérique
• Pavé tactile : glissez pour naviguer, touchez pour valider
• Clavier : tapez vos recherches et mots de passe depuis le téléphone
• Allumage de la TV, même éteinte (si la TV le permet)

⭐ OMNIZAP PRO (ACHAT UNIQUE, 7 JOURS D'ESSAI GRATUIT)
• Plusieurs TV
• Applications de la TV avec leurs icônes, en un geste
• Commandes vocales : « Ouvre Netflix »
• Photos et vidéos du téléphone sur la TV, en diaporama
• Duplication de l'écran du téléphone sur la TV, avec le son (Android TV)
• « Voir la TV » : l'écran de la TV en direct sur le téléphone, avec capture (Android TV)
• Télécommande Bluetooth
• Widget, télécommande dans les notifications et réglages rapides

Pas d'abonnement, pas de publicité : un paiement unique débloque Pro pour toujours.

🔒 VOS DONNÉES RESTENT CHEZ VOUS
OmniZap communique uniquement avec votre TV, sur votre réseau local. Aucun serveur, aucune mesure d'audience.

🌍 15 LANGUES
Français, English, Español, Português, Deutsch, Italiano, हिन्दी, العربية, الدارجة, Hausa, Yorùbá, Wolof, Lingála, Kiswahili, አማርኛ.

OmniZap est une application indépendante, non affiliée aux fabricants de TV cités.""")

L["en-US"] = ("OmniZap - TV Remote",
"Universal remote for Android TV, Google TV, Samsung, LG and infrared TVs.",
"""Turn your phone into a remote for all your TVs.

OmniZap controls your TV over Wi-Fi, with the phone's infrared blaster, or over Bluetooth. No account, no ads: your settings stay on your phone.

📺 WORKS WITH
• Android TV and Google TV (Sony, TCL, Philips, Sharp, Hisense, Xiaomi, Chromecast with Google TV…)
• Samsung (Tizen, 2016 and later) and LG (webOS) TVs over Wi-Fi
• Any infrared TV, if your phone has an IR blaster (many Xiaomi, Redmi, Poco, Huawei, Honor)
• Bluetooth (experimental): some TVs refuse remotes coming from a phone

🎮 THE REMOTE (FREE)
• Every key: arrows, OK, back, home, volume, channels, playback, number pad
• Touchpad: swipe to navigate, tap to select
• Keyboard: type searches and passwords from your phone
• Turn the TV on, even when it is off (if the TV allows it)

⭐ OMNIZAP PRO (ONE-TIME PURCHASE, 7-DAY FREE TRIAL)
• Multiple TVs
• TV apps with their icons, one tap away
• Voice commands: "Open Netflix"
• Phone photos and videos on the TV, as a slideshow
• Mirror your phone screen to the TV, with sound (Android TV)
• "Watch TV": the TV screen live on your phone, with screenshots (Android TV)
• Bluetooth remote
• Widget, remote in notifications and quick settings

No subscription, no ads: a single payment unlocks Pro forever.

🔒 YOUR DATA STAYS WITH YOU
OmniZap only talks to your TV, on your local network. No servers, no analytics.

🌍 15 LANGUAGES
Français, English, Español, Português, Deutsch, Italiano, हिन्दी, العربية, الدارجة, Hausa, Yorùbá, Wolof, Lingála, Kiswahili, አማርኛ.

OmniZap is an independent app, not affiliated with the TV manufacturers mentioned.""")

L["es-ES"] = ("OmniZap - Mando para TV",
"Mando universal: Android TV, Google TV, Samsung, LG y televisores infrarrojos.",
"""Convierte tu teléfono en el mando de todos tus televisores.

OmniZap controla tu televisor por Wi-Fi, con el emisor infrarrojo del teléfono o por Bluetooth. Sin cuenta ni publicidad: tus ajustes se quedan en tu teléfono.

📺 COMPATIBLE
• Android TV y Google TV (Sony, TCL, Philips, Sharp, Hisense, Xiaomi, Chromecast con Google TV…)
• Televisores Samsung (Tizen, 2016 o posteriores) y LG (webOS) por Wi-Fi
• Cualquier televisor infrarrojo, si tu teléfono tiene emisor (muchos Xiaomi, Redmi, Poco, Huawei, Honor)
• Bluetooth (experimental): algunos televisores rechazan mandos que vienen de un teléfono

🎮 EL MANDO (GRATIS)
• Todas las teclas: flechas, OK, atrás, inicio, volumen, canales, reproducción, teclado numérico
• Panel táctil: desliza para navegar, toca para aceptar
• Teclado: escribe búsquedas y contraseñas desde el teléfono
• Encender el televisor, incluso apagado (si el televisor lo permite)

⭐ OMNIZAP PRO (PAGO ÚNICO, 7 DÍAS DE PRUEBA GRATIS)
• Varios televisores
• Las aplicaciones del televisor con sus iconos, en un toque
• Comandos de voz: «Abre Netflix»
• Fotos y vídeos del teléfono en el televisor, en presentación
• Duplicar la pantalla del teléfono en el televisor, con sonido (Android TV)
• «Ver la TV»: la pantalla del televisor en directo en el teléfono, con captura (Android TV)
• Mando Bluetooth
• Widget, mando en las notificaciones y ajustes rápidos

Sin suscripción ni publicidad: un único pago desbloquea Pro para siempre.

🔒 TUS DATOS SE QUEDAN CONTIGO
OmniZap solo se comunica con tu televisor, en tu red local. Sin servidores ni estadísticas.

🌍 15 IDIOMAS
Français, English, Español, Português, Deutsch, Italiano, हिन्दी, العربية, الدارجة, Hausa, Yorùbá, Wolof, Lingála, Kiswahili, አማርኛ.

OmniZap es una aplicación independiente, no afiliada a los fabricantes de televisores mencionados.""")

L["pt-BR"] = ("OmniZap - Controle de TV",
"Controle universal: Android TV, Google TV, Samsung, LG e TVs infravermelho.",
"""Transforme seu celular no controle de todas as suas TVs.

O OmniZap controla sua TV pelo Wi-Fi, pelo emissor infravermelho do celular ou por Bluetooth. Sem conta, sem anúncios: suas configurações ficam no celular.

📺 COMPATÍVEL
• Android TV e Google TV (Sony, TCL, Philips, Sharp, Hisense, Xiaomi, Chromecast com Google TV…)
• TVs Samsung (Tizen, 2016 em diante) e LG (webOS) pelo Wi-Fi
• Qualquer TV infravermelho, se o celular tiver emissor (muitos Xiaomi, Redmi, Poco, Huawei, Honor)
• Bluetooth (experimental): algumas TVs recusam controles vindos de um celular

🎮 O CONTROLE (GRÁTIS)
• Todas as teclas: setas, OK, voltar, início, volume, canais, reprodução, teclado numérico
• Touchpad: deslize para navegar, toque para confirmar
• Teclado: digite buscas e senhas pelo celular
• Ligar a TV, mesmo desligada (se a TV permitir)

⭐ OMNIZAP PRO (COMPRA ÚNICA, 7 DIAS DE TESTE GRÁTIS)
• Várias TVs
• Os apps da TV com seus ícones, em um toque
• Comandos de voz: «Abrir Netflix»
• Fotos e vídeos do celular na TV, em apresentação
• Espelhamento da tela do celular na TV, com som (Android TV)
• «Ver a TV»: a tela da TV ao vivo no celular, com captura (Android TV)
• Controle Bluetooth
• Widget, controle nas notificações e configurações rápidas

Sem assinatura, sem anúncios: um pagamento único libera o Pro para sempre.

🔒 SEUS DADOS FICAM COM VOCÊ
O OmniZap só conversa com a sua TV, na sua rede local. Sem servidores, sem estatísticas.

🌍 15 IDIOMAS
Français, English, Español, Português, Deutsch, Italiano, हिन्दी, العربية, الدارجة, Hausa, Yorùbá, Wolof, Lingála, Kiswahili, አማርኛ.

O OmniZap é um app independente, sem vínculo com os fabricantes de TV citados.""")

L["de-DE"] = ("OmniZap – TV-Fernbedienung",
"Universalfernbedienung: Android TV, Google TV, Samsung, LG und Infrarot-TVs.",
"""Machen Sie Ihr Handy zur Fernbedienung für alle Ihre Fernseher.

OmniZap steuert Ihren TV per WLAN, mit dem Infrarotsender des Handys oder per Bluetooth. Kein Konto, keine Werbung: Ihre Einstellungen bleiben auf dem Handy.

📺 KOMPATIBEL
• Android TV und Google TV (Sony, TCL, Philips, Sharp, Hisense, Xiaomi, Chromecast mit Google TV…)
• Samsung-TVs (Tizen, ab 2016) und LG-TVs (webOS) per WLAN
• Jeder Infrarot-TV, wenn Ihr Handy einen IR-Sender hat (viele Xiaomi, Redmi, Poco, Huawei, Honor)
• Bluetooth (experimentell): Manche TVs lehnen Fernbedienungen von Handys ab

🎮 DIE FERNBEDIENUNG (KOSTENLOS)
• Alle Tasten: Pfeile, OK, Zurück, Home, Lautstärke, Sender, Wiedergabe, Ziffernblock
• Touchpad: wischen zum Navigieren, tippen zum Bestätigen
• Tastatur: Suchen und Passwörter am Handy eintippen
• TV einschalten, auch wenn er aus ist (wenn der TV es erlaubt)

⭐ OMNIZAP PRO (EINMALKAUF, 7 TAGE KOSTENLOS TESTEN)
• Mehrere TVs
• Die Apps des TVs mit ihren Symbolen, mit einem Tippen
• Sprachbefehle: „Öffne Netflix“
• Fotos und Videos vom Handy auf dem TV, als Diashow
• Handybildschirm auf den TV spiegeln, mit Ton (Android TV)
• „TV ansehen“: der TV-Bildschirm live auf dem Handy, mit Screenshot (Android TV)
• Bluetooth-Fernbedienung
• Widget, Fernbedienung in den Benachrichtigungen und Schnelleinstellungen

Kein Abo, keine Werbung: Eine einmalige Zahlung schaltet Pro für immer frei.

🔒 IHRE DATEN BLEIBEN BEI IHNEN
OmniZap spricht nur mit Ihrem TV, in Ihrem Heimnetz. Keine Server, keine Statistiken.

🌍 15 SPRACHEN
Français, English, Español, Português, Deutsch, Italiano, हिन्दी, العربية, الدارجة, Hausa, Yorùbá, Wolof, Lingála, Kiswahili, አማርኛ.

OmniZap ist eine unabhängige App und steht in keiner Verbindung zu den genannten TV-Herstellern.""")

L["it-IT"] = ("OmniZap - Telecomando TV",
"Telecomando universale: Android TV, Google TV, Samsung, LG e TV a infrarossi.",
"""Trasforma il telefono nel telecomando di tutte le tue TV.

OmniZap controlla la TV via Wi-Fi, con l'emettitore infrarossi del telefono o in Bluetooth. Nessun account, nessuna pubblicità: le impostazioni restano sul telefono.

📺 COMPATIBILE
• Android TV e Google TV (Sony, TCL, Philips, Sharp, Hisense, Xiaomi, Chromecast con Google TV…)
• TV Samsung (Tizen, dal 2016) e LG (webOS) via Wi-Fi
• Qualsiasi TV a infrarossi, se il telefono ha l'emettitore (molti Xiaomi, Redmi, Poco, Huawei, Honor)
• Bluetooth (sperimentale): alcune TV rifiutano i telecomandi provenienti da un telefono

🎮 IL TELECOMANDO (GRATIS)
• Tutti i tasti: frecce, OK, indietro, home, volume, canali, riproduzione, tastierino numerico
• Touchpad: scorri per navigare, tocca per confermare
• Tastiera: scrivi ricerche e password dal telefono
• Accendi la TV, anche spenta (se la TV lo consente)

⭐ OMNIZAP PRO (ACQUISTO UNICO, 7 GIORNI DI PROVA GRATUITA)
• Più TV
• Le app della TV con le loro icone, con un tocco
• Comandi vocali: «Apri Netflix»
• Foto e video del telefono sulla TV, in presentazione
• Duplica lo schermo del telefono sulla TV, con audio (Android TV)
• «Guarda la TV»: lo schermo della TV in diretta sul telefono, con screenshot (Android TV)
• Telecomando Bluetooth
• Widget, telecomando nelle notifiche e impostazioni rapide

Nessun abbonamento, nessuna pubblicità: un pagamento unico sblocca Pro per sempre.

🔒 I TUOI DATI RESTANO TUOI
OmniZap comunica solo con la tua TV, sulla rete locale. Nessun server, nessuna statistica.

🌍 15 LINGUE
Français, English, Español, Português, Deutsch, Italiano, हिन्दी, العربية, الدارجة, Hausa, Yorùbá, Wolof, Lingála, Kiswahili, አማርኛ.

OmniZap è un'app indipendente, non affiliata ai produttori di TV citati.""")

L["ar"] = ("OmniZap - جهاز تحكم التلفاز",
"جهاز تحكم شامل: Android TV وGoogle TV وSamsung وLG وأجهزة الأشعة تحت الحمراء.",
"""حوّل هاتفك إلى جهاز تحكم لكل أجهزة التلفاز لديك.

يتحكم OmniZap في تلفازك عبر Wi-Fi أو بباعث الأشعة تحت الحمراء في الهاتف أو بالبلوتوث. بدون حساب وبدون إعلانات: تبقى إعداداتك على هاتفك.

📺 متوافق مع
• Android TV وGoogle TV (Sony وTCL وPhilips وSharp وHisense وXiaomi وChromecast مع Google TV…)
• أجهزة Samsung (Tizen، من 2016) وLG (webOS) عبر Wi-Fi
• أي تلفاز يعمل بالأشعة تحت الحمراء إذا كان هاتفك مزودًا بباعث (كثير من Xiaomi وRedmi وPoco وHuawei وHonor)
• البلوتوث (تجريبي): بعض أجهزة التلفاز ترفض أجهزة التحكم القادمة من هاتف

🎮 جهاز التحكم (مجاني)
• كل الأزرار: الأسهم وOK والرجوع والرئيسية والصوت والقنوات والتشغيل ولوحة الأرقام
• لوحة لمس: اسحب للتنقل واضغط للتأكيد
• لوحة مفاتيح: اكتب عمليات البحث وكلمات المرور من الهاتف
• تشغيل التلفاز حتى وهو مطفأ (إذا سمح التلفاز بذلك)

⭐ OMNIZAP PRO (شراء لمرة واحدة، تجربة مجانية 7 أيام)
• عدة أجهزة تلفاز
• تطبيقات التلفاز مع أيقوناتها بلمسة واحدة
• أوامر صوتية: «افتح Netflix»
• صور وفيديوهات الهاتف على التلفاز كعرض شرائح
• عكس شاشة الهاتف على التلفاز مع الصوت (Android TV)
• «مشاهدة التلفاز»: شاشة التلفاز مباشرة على الهاتف مع لقطة شاشة (Android TV)
• جهاز تحكم بلوتوث
• أداة وجهاز تحكم في الإشعارات والإعدادات السريعة

بدون اشتراك وبدون إعلانات: دفعة واحدة تفتح Pro إلى الأبد.

🔒 بياناتك تبقى لك
يتواصل OmniZap مع تلفازك فقط، على شبكتك المحلية. بدون خوادم وبدون إحصاءات.

🌍 15 لغة
Français, English, Español, Português, Deutsch, Italiano, हिन्दी, العربية, الدارجة, Hausa, Yorùbá, Wolof, Lingála, Kiswahili, አማርኛ.

OmniZap تطبيق مستقل وغير تابع لمصنّعي أجهزة التلفاز المذكورين.""")

L["hi-IN"] = ("OmniZap - टीवी रिमोट",
"यूनिवर्सल रिमोट: Android TV, Google TV, Samsung, LG और इन्फ्रारेड टीवी।",
"""अपने फ़ोन को अपने सभी टीवी का रिमोट बनाइए।

OmniZap आपके टीवी को Wi-Fi, फ़ोन के इन्फ्रारेड एमिटर या Bluetooth से चलाता है। कोई खाता नहीं, कोई विज्ञापन नहीं: आपकी सेटिंग्स आपके फ़ोन पर ही रहती हैं।

📺 इनके साथ काम करता है
• Android TV और Google TV (Sony, TCL, Philips, Sharp, Hisense, Xiaomi, Chromecast with Google TV…)
• Samsung (Tizen, 2016 और बाद के) और LG (webOS) टीवी, Wi-Fi से
• कोई भी इन्फ्रारेड टीवी, अगर आपके फ़ोन में IR एमिटर है (कई Xiaomi, Redmi, Poco, Huawei, Honor)
• Bluetooth (प्रायोगिक): कुछ टीवी फ़ोन से आने वाले रिमोट स्वीकार नहीं करते

🎮 रिमोट (मुफ़्त)
• सभी बटन: तीर, OK, वापस, होम, आवाज़, चैनल, प्लेबैक, नंबर पैड
• टचपैड: चलाने के लिए स्वाइप करें, चुनने के लिए टैप करें
• कीबोर्ड: फ़ोन से सर्च और पासवर्ड टाइप करें
• टीवी चालू करें, बंद होने पर भी (अगर टीवी अनुमति दे)

⭐ OMNIZAP PRO (एक बार की ख़रीद, 7 दिन मुफ़्त ट्रायल)
• कई टीवी
• टीवी के ऐप्स उनके आइकन के साथ, एक टैप में
• वॉइस कमांड: «Netflix खोलो»
• फ़ोन की फ़ोटो और वीडियो टीवी पर, स्लाइडशो में
• फ़ोन की स्क्रीन टीवी पर मिरर करें, आवाज़ के साथ (Android TV)
• «टीवी देखें»: टीवी की स्क्रीन फ़ोन पर लाइव, स्क्रीनशॉट के साथ (Android TV)
• Bluetooth रिमोट
• विजेट, नोटिफ़िकेशन में रिमोट और क्विक सेटिंग्स

कोई सब्सक्रिप्शन नहीं, कोई विज्ञापन नहीं: एक बार का भुगतान Pro को हमेशा के लिए खोल देता है।

🔒 आपका डेटा आपके पास रहता है
OmniZap सिर्फ़ आपके टीवी से, आपके लोकल नेटवर्क पर बात करता है। कोई सर्वर नहीं, कोई आँकड़े नहीं।

🌍 15 भाषाएँ
Français, English, Español, Português, Deutsch, Italiano, हिन्दी, العربية, الدارجة, Hausa, Yorùbá, Wolof, Lingála, Kiswahili, አማርኛ.

OmniZap एक स्वतंत्र ऐप है, बताए गए टीवी निर्माताओं से संबद्ध नहीं।""")

L["sw"] = ("OmniZap - Rimoti ya TV",
"Rimoti ya ulimwengu: Android TV, Google TV, Samsung, LG na TV za infraredi.",
"""Geuza simu yako kuwa rimoti ya TV zako zote.

OmniZap inaendesha TV yako kwa Wi-Fi, kwa kitoa mwanga wa infraredi cha simu au kwa Bluetooth. Hakuna akaunti, hakuna matangazo: mipangilio yako inabaki kwenye simu.

📺 INAFANYA KAZI NA
• Android TV na Google TV (Sony, TCL, Philips, Sharp, Hisense, Xiaomi, Chromecast yenye Google TV…)
• TV za Samsung (Tizen, 2016 na baadaye) na LG (webOS) kwa Wi-Fi
• TV yoyote ya infraredi, ikiwa simu yako ina kitoa mwanga (Xiaomi, Redmi, Poco, Huawei, Honor nyingi)
• Bluetooth (majaribio): baadhi ya TV hukataa rimoti kutoka kwa simu

🎮 RIMOTI (BURE)
• Vitufe vyote: mishale, OK, rudi, nyumbani, sauti, chaneli, uchezaji, nambari
• Padi ya kugusa: telezesha kusogea, gusa kuchagua
• Kibodi: andika utafutaji na nenosiri kutoka kwenye simu
• Washa TV, hata ikiwa imezimwa (ikiwa TV inaruhusu)

⭐ OMNIZAP PRO (MALIPO YA MARA MOJA, MAJARIBIO YA BURE SIKU 7)
• TV nyingi
• Programu za TV pamoja na aikoni zake, kwa mguso mmoja
• Amri za sauti: «Fungua Netflix»
• Picha na video za simu kwenye TV, kama onyesho
• Akisi skrini ya simu kwenye TV, pamoja na sauti (Android TV)
• «Tazama TV»: skrini ya TV moja kwa moja kwenye simu, pamoja na picha ya skrini (Android TV)
• Rimoti ya Bluetooth
• Wijeti, rimoti kwenye arifa na mipangilio ya haraka

Hakuna usajili, hakuna matangazo: malipo moja yanafungua Pro milele.

🔒 DATA YAKO INABAKI KWAKO
OmniZap inawasiliana na TV yako tu, kwenye mtandao wako wa ndani. Hakuna seva, hakuna takwimu.

🌍 LUGHA 15
Français, English, Español, Português, Deutsch, Italiano, हिन्दी, العربية, الدارجة, Hausa, Yorùbá, Wolof, Lingála, Kiswahili, አማርኛ.

OmniZap ni programu huru, haihusiani na watengenezaji wa TV waliotajwa.""")

base = os.path.join(os.path.dirname(os.path.abspath(__file__)), "metadata", "android")
for lang, (title, short, full) in L.items():
    assert len(title) <= 30, (lang, len(title))
    assert len(short) <= 80, (lang, len(short))
    assert len(full) <= 4000, (lang, len(full))
    d = os.path.join(base, lang)
    os.makedirs(d, exist_ok=True)
    for name, text in (("title", title), ("short_description", short), ("full_description", full)):
        with open(os.path.join(d, name + ".txt"), "w", encoding="utf-8") as f:
            f.write(text.strip() + "\n")
    print(lang, len(title), len(short), len(full))
