# -*- coding: utf-8 -*-
"""앱에 넣는 오프라인 문장 꾸러미(bank.json)를 만든다.
형식: (topic, kind, long, 한국어 지시, 목표 문장, 한국어 뜻, 시연용 답변)
kind: listen=듣기만, repeat=따라 말하기, respond=상황에 맞게 말하기, quiz=동승자 퀴즈
"""
import json, sys

EN = [
# 워밍업
("warmup","repeat",0,"출발 인사부터 해 볼게요. 따라 말해 보세요.","Good morning! Let's get started.","좋은 아침! 시작해 볼까요.","Good morning, let's get started."),
("warmup","respond",0,"오늘 기분이 어떤지 영어로 짧게 말해 보세요.","I'm feeling pretty good today.","오늘 기분이 꽤 좋아요.","I feel good today."),
("warmup","repeat",0,"오늘 하루 계획을 말하는 표현이에요.","I have a busy day ahead.","오늘 바쁜 하루가 될 거예요.","I have a busy day ahead."),
("warmup","respond",1,"어젯밤 잘 잤는지 묻는 동료에게 대답해 보세요.","I slept well, thanks. How about you?","잘 잤어요, 고마워요. 당신은요?","I slept well thank you, how about you?"),
# 일상
("smalltalk","repeat",0,"주말 계획을 묻는 표현이에요.","Do you have any plans for the weekend?","주말에 계획 있어요?","Do you have any plan for the weekend?"),
("smalltalk","respond",1,"동료가 주말에 뭐 했냐고 물어요. 등산 갔다고 말해 보세요.","I went hiking with my friends.","친구들이랑 등산 갔어요.","I went hiking with my friends."),
("smalltalk","repeat",0,"맞장구 표현이에요.","That sounds like a lot of fun.","정말 재밌었겠네요.","That sounds a lot of fun."),
("smalltalk","respond",1,"요즘 바쁘냐는 질문에, 프로젝트 마감 때문에 바쁘다고 답해 보세요.","I've been busy with a project deadline.","프로젝트 마감 때문에 바빴어요.","I have been busy with project deadline."),
# 회의(목적지가 회사)
("office_meeting","respond",1,"회의를 3시로 미루자고 말해 보세요.","Can we push the meeting to three?","회의를 3시로 미룰 수 있을까요?","Can we push the meeting to three?"),
("office_meeting","repeat",0,"회의를 시작할 때 쓰는 표현이에요.","Let's get down to business.","본론으로 들어가죠.","Let's get down to business."),
("office_meeting","respond",1,"상대 말을 잘 못 들었어요. 다시 말해 달라고 정중하게 부탁해 보세요.","Could you say that again, please?","다시 한번 말씀해 주시겠어요?","Could you say again please?"),
("office_meeting","respond",1,"이 건은 오늘 안에 이메일로 정리해서 보내겠다고 말해 보세요.","I'll send you a summary by email today.","오늘 이메일로 요약해서 보낼게요.","I will send you a summary by email today."),
("office_meeting","repeat",0,"의견에 동의하는 표현이에요.","I'm on the same page.","저도 같은 생각이에요.","I'm on the same page."),
("office_meeting","respond",1,"화면 공유가 잘 보이는지 물어보세요.","Can everyone see my screen?","제 화면 다 보이세요?","Can everyone see my screen?"),
("office_meeting","respond",1,"마감일을 하루 늦출 수 있는지 물어보세요.","Is it possible to extend the deadline by one day?","마감을 하루 늦출 수 있을까요?","Is it possible to extend the deadline one day?"),
# 길 안내
("directions","repeat",0,"다음 교차로에서 오른쪽으로 가라는 표현이에요.","Turn right at the next intersection.","다음 교차로에서 우회전하세요.","Turn right at the next intersection."),
("directions","repeat",0,"왼쪽으로 꺾을 때 쓰는 표현이에요.","Take a left at the light.","신호등에서 좌회전하세요.","Take a left at the light."),
("directions","respond",1,"길을 잃은 관광객에게 이 길로 쭉 가라고 말해 보세요.","Just go straight down this road.","이 길로 쭉 가세요.","Just go straight down this road."),
("directions","respond",1,"목적지까지 얼마나 걸리는지 물어보세요.","How long does it take to get there?","거기까지 얼마나 걸려요?","How long does it take to get there?"),
("directions","repeat",0,"차선을 바꿔야 할 때 쓰는 표현이에요.","I need to change lanes.","차선을 바꿔야 해요.","I need to change lane."),
("directions","respond",1,"고속도로 출구를 놓쳤다고 말해 보세요.","I think I missed the exit.","출구를 놓친 것 같아요.","I think I missed the exit."),
# 표지·규칙
("road_signs","repeat",0,"과속 카메라를 알리는 표현이에요.","There's a speed camera ahead.","앞에 과속 카메라가 있어요.","There is a speed camera ahead."),
("road_signs","respond",0,"여기 제한속도가 얼마인지 물어보세요.","What's the speed limit here?","여기 제한속도가 얼마예요?","What is the speed limit here?"),
("road_signs","repeat",0,"속도를 줄이자는 표현이에요.","Let's slow down a little.","조금 천천히 가요.","Let's slow down a little."),
# 어린이 보호구역(듣기만)
("school_zone","listen",0,"어린이 보호구역이에요. 듣기만 하세요.","School zone. Let's slow down.","어린이 보호구역. 천천히 가요.",""),
("school_zone","listen",0,"주변을 살피며 듣기만 하세요.","Watch out for children crossing.","길 건너는 아이들을 조심하세요.",""),
("school_zone","listen",0,"듣기만 하세요.","The speed limit is thirty here.","여기 제한속도는 30이에요.",""),
# 정체
("traffic","respond",1,"동료에게 차가 막혀서 10분 늦는다고 말해 보세요.","I'm stuck in traffic. I'll be ten minutes late.","차가 막혀요. 10분 늦을 것 같아요.","I'm stuck in traffic, I will be ten minutes late."),
("traffic","repeat",0,"출퇴근 정체를 말하는 표현이에요.","Traffic is bumper to bumper.","차가 꽉 막혔어요.","Traffic is bumper to bumper."),
("traffic","respond",1,"사고 때문에 길이 막힌 것 같다고 말해 보세요.","There must be an accident up ahead.","앞에 사고가 났나 봐요.","There must be accident up ahead."),
("traffic","respond",0,"먼저 회의를 시작해 달라고 부탁해 보세요.","Please start without me.","저 없이 먼저 시작해 주세요.","Please start without me."),
# 비
("weather_rain","repeat",0,"비가 온다는 표현이에요.","It's pouring outside.","밖에 비가 쏟아져요.","It's pouring outside."),
("weather_rain","respond",1,"우산을 안 가져왔다고 말해 보세요.","I forgot to bring my umbrella.","우산 가져오는 걸 깜빡했어요.","I forgot to bring umbrella."),
("weather_rain","respond",1,"오늘 비가 그칠지 물어보세요.","Do you think the rain will stop today?","오늘 비가 그칠까요?","Do you think the rain will stop today?"),
("weather_rain","repeat",0,"비 오는 날 운전 표현이에요.","The roads are slippery today.","오늘 길이 미끄러워요.","The roads are slippery today."),
# 앞에 보이는 곳({name}, {place})
("landmark","respond",1,"곧 {name}이 나와요. {place}에 가는 길을 물어보세요.","Excuse me, how do I get to {name}?","실례지만 {name}에 어떻게 가요?","Excuse me, how do I get to {name}?"),
("landmark","respond",1,"{name} 근처에서 만나자고 말해 보세요.","Let's meet near {name}.","{name} 근처에서 만나요.","Let's meet near {name}."),
("landmark","repeat",0,"앞에 보이는 곳을 가리키는 표현이에요.","It's right next to {name}.","{name} 바로 옆이에요.","It's right next to {name}."),
("landmark","respond",1,"{name}이 몇 시에 문을 여는지 물어보세요.","What time does {name} open?","{name} 몇 시에 열어요?","What time does {name} open?"),
# 충전·주유
("charging","respond",1,"충전소 직원에게 급속 충전기가 있는지 물어보세요.","Do you have a fast charger here?","여기 급속 충전기 있어요?","Do you have a fast charger here?"),
("charging","repeat",0,"배터리가 얼마 안 남았다는 표현이에요.","My battery is running low.","배터리가 얼마 안 남았어요.","My battery is running low."),
("charging","respond",1,"가득 채워 달라고 말해 보세요.","Fill it up, please.","가득 채워 주세요.","Fill it up please."),
# 온도
("temperature","respond",0,"좀 춥다고 말해 보세요.","It's a bit chilly in here.","여기 좀 쌀쌀해요.","It's a bit chilly in here."),
("temperature","respond",1,"에어컨을 좀 줄여 달라고 부탁해 보세요.","Could you turn down the AC a little?","에어컨 좀 약하게 해 줄래요?","Could you turn down the AC little?"),
# 장보기
("shopping","respond",1,"우유가 어디 있는지 물어보세요.","Where can I find the milk?","우유 어디 있어요?","Where can I find milk?"),
("shopping","respond",1,"카드로 계산해도 되는지 물어보세요.","Can I pay by card?","카드로 계산돼요?","Can I pay by card?"),
("shopping","repeat",0,"봉투가 필요하다는 표현이에요.","Can I get a bag, please?","봉투 하나 주세요.","Can I get a bag please?"),
# 여행
("travel","respond",1,"렌터카 카운터에서 예약했다고 말해 보세요.","I have a reservation under Kim.","김으로 예약했어요.","I have a reservation under Kim."),
("travel","respond",1,"보험이 포함되어 있는지 물어보세요.","Is insurance included?","보험 포함이에요?","Is insurance included?"),
# 병원
("health","respond",1,"진료 예약을 하고 싶다고 말해 보세요.","I'd like to make an appointment.","진료 예약하고 싶어요.","I would like to make an appointment."),
# 동승자 퀴즈
("family_quiz","quiz",0,"함께 퀴즈! '빨간 신호등'을 영어로 하면?","Red light","빨간 신호등","Red light"),
("family_quiz","quiz",0,"함께 퀴즈! '다리'를 영어로 하면?","Bridge","다리","Bridge"),
("family_quiz","quiz",0,"함께 퀴즈! '구름'을 영어로 하면?","Cloud","구름","Cloud"),
("family_quiz","quiz",0,"함께 퀴즈! '버스 정류장'을 영어로 하면?","Bus stop","버스 정류장","Bus stop"),
("family_quiz","quiz",0,"함께 퀴즈! '터널'을 영어로 하면?","Tunnel","터널","Tunnel"),
]

ES = [
("warmup","repeat",0,"출발 인사예요. 스페인어로 따라 말해 보세요.","¡Buenos días! Vamos a empezar.","좋은 아침! 시작해요.","Buenos días, vamos a empezar."),
("smalltalk","respond",1,"주말에 뭐 했는지 묻는 표현이에요.","¿Qué hiciste el fin de semana?","주말에 뭐 했어요?","¿Qué hiciste el fin de semana?"),
("directions","repeat",0,"다음 교차로에서 우회전하라는 표현이에요.","Gire a la derecha en el próximo cruce.","다음 교차로에서 우회전하세요.","Gire a la derecha en el próximo cruce."),
("directions","respond",1,"이 길로 쭉 가라고 말해 보세요.","Siga todo recto por esta calle.","이 길로 쭉 가세요.","Siga todo recto por esta calle."),
("traffic","respond",1,"차가 막혀서 늦는다고 말해 보세요.","Hay mucho tráfico. Voy a llegar tarde.","차가 많이 막혀요. 늦을 거예요.","Hay mucho tráfico, voy a llegar tarde."),
("weather_rain","repeat",0,"비가 온다는 표현이에요.","Está lloviendo mucho.","비가 많이 와요.","Está lloviendo mucho."),
("charging","respond",1,"가득 채워 달라고 말해 보세요.","Lleno, por favor.","가득 채워 주세요.","Lleno por favor."),
("travel","respond",1,"렌터카를 예약했다고 말해 보세요.","Tengo una reserva a nombre de Kim.","김으로 예약했어요.","Tengo una reserva a nombre de Kim."),
("travel","respond",1,"보험이 포함되어 있는지 물어보세요.","¿Está incluido el seguro?","보험 포함이에요?","¿Está incluido el seguro?"),
("office_meeting","respond",1,"회의를 3시로 미루자고 말해 보세요.","¿Podemos mover la reunión a las tres?","회의를 3시로 옮길 수 있을까요?","¿Podemos mover la reunión a las tres?"),
("school_zone","listen",0,"듣기만 하세요.","Zona escolar. Vamos despacio.","어린이 보호구역. 천천히 가요.",""),
("road_signs","repeat",0,"앞에 과속 카메라가 있다는 표현이에요.","Hay un radar adelante.","앞에 과속 카메라가 있어요.","Hay un radar adelante."),
("landmark","respond",1,"{name}에 어떻게 가는지 물어보세요.","Disculpe, ¿cómo llego a {name}?","실례지만 {name}에 어떻게 가요?","Disculpe, ¿cómo llego a {name}?"),
("family_quiz","quiz",0,"함께 퀴즈! '자동차'를 스페인어로 하면?","Coche","자동차","Coche"),
]

FR = [
("warmup","repeat",0,"출발 인사예요. 프랑스어로 따라 말해 보세요.","Bonjour ! On commence.","안녕하세요! 시작해요.","Bonjour, on commence."),
("smalltalk","respond",1,"주말 잘 보냈는지 물어보세요.","Tu as passé un bon week-end ?","주말 잘 보냈어?","Tu as passé un bon week-end ?"),
("directions","repeat",0,"다음 교차로에서 우회전하라는 표현이에요.","Tournez à droite au prochain carrefour.","다음 교차로에서 우회전하세요.","Tournez à droite au prochain carrefour."),
("directions","respond",1,"이 길로 쭉 가라고 말해 보세요.","Continuez tout droit sur cette rue.","이 길로 쭉 가세요.","Continuez tout droit sur cette rue."),
("traffic","respond",1,"차가 막혀서 늦는다고 말해 보세요.","Je suis coincé dans les bouchons. Je serai en retard.","차가 막혀요. 늦을 거예요.","Je suis coincé dans les bouchons, je serai en retard."),
("weather_rain","repeat",0,"비가 온다는 표현이에요.","Il pleut beaucoup aujourd'hui.","오늘 비가 많이 와요.","Il pleut beaucoup aujourd'hui."),
("office_meeting","respond",1,"회의를 3시로 미루자고 말해 보세요.","On peut décaler la réunion à quinze heures ?","회의를 3시로 옮길 수 있을까요?","On peut décaler la réunion à quinze heures ?"),
("charging","respond",1,"급속 충전기가 있는지 물어보세요.","Vous avez une borne de recharge rapide ?","급속 충전기 있어요?","Vous avez une borne de recharge rapide ?"),
("school_zone","listen",0,"듣기만 하세요.","Zone scolaire. On ralentit.","어린이 보호구역. 속도를 줄여요.",""),
("road_signs","repeat",0,"앞에 과속 카메라가 있다는 표현이에요.","Il y a un radar devant.","앞에 과속 카메라가 있어요.","Il y a un radar devant."),
("landmark","respond",1,"{name}에 어떻게 가는지 물어보세요.","Excusez-moi, comment aller à {name} ?","실례지만 {name}에 어떻게 가요?","Excusez-moi, comment aller à {name} ?"),
("family_quiz","quiz",0,"함께 퀴즈! '자동차'를 프랑스어로 하면?","Voiture","자동차","Voiture"),
]

out = []
for lang, rows in (("en", EN), ("es", ES), ("fr", FR)):
    for i, (topic, kind, long_, pko, tgt, hko, demo) in enumerate(rows):
        out.append(dict(id=f"{lang}-{topic}-{i}", topic=topic, lang=lang, kind=kind, promptKo=pko,
                        target=tgt, hintKo=hko, expected=tgt, long=bool(long_), demoAnswer=demo))
json.dump(out, open(sys.argv[1], "w", encoding="utf-8"), ensure_ascii=False, indent=0)
print(len(out), "items")
