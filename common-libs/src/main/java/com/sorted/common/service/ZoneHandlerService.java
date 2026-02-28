package com.sorted.common.service;


import com.sorted.common.beans.CreateZoneRequest;
import com.sorted.common.constants.Defaults;
import com.sorted.common.entity.mongo.Address;
import com.sorted.common.entity.mongo.BaseMongoEntity;
import com.sorted.common.entity.mongo.Seller;
import com.sorted.common.entity.mongo.ZoneEntity;
import com.sorted.common.entity.service.Address_Service;
import com.sorted.common.entity.service.Seller_Service;
import com.sorted.common.entity.service.ZoneService;
import com.sorted.common.enums.ResponseCode;
import com.sorted.common.exceptions.DeliveryNotAvailableException;
import com.sorted.common.helper.AggregationFilter.SEFilter;
import com.sorted.common.helper.AggregationFilter.SEFilterType;
import com.sorted.common.helper.AggregationFilter.WhereClause;
import com.sorted.common.utils.DistanceUtils;
import com.sorted.common.utils.GeoUtils;
import com.sorted.common.utils.Preconditions;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.geo.GeoJsonPolygon;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ZoneHandlerService {

    private final ZoneService zoneService;
    private final Seller_Service sellerService;
    private final Address_Service addressService;

    public void createZone(CreateZoneRequest request) {

        List<Point> points = request.coordinates()
                .get(0)
                .stream()
                .map(coordinate -> new Point(coordinate.get(0), coordinate.get(1))) // lng, lat
                .toList();

        GeoJsonPolygon polygon = new GeoJsonPolygon(points);

        ZoneEntity entity = ZoneEntity.builder()
                .zoneId(request.zoneId())
                .name(request.name())
                .geometry(polygon)
                .build();

        zoneService.create(entity, Defaults.SYSTEM_ADMIN);
    }

    public ZoneEntity identifyZone(double lat, double lng) {
        List<ZoneEntity> zoneEntities = zoneService.repoFindAll();
        for (ZoneEntity zoneEntity : zoneEntities) {
            if (GeoUtils.contains(zoneEntity.getGeometry(), lat, lng)) {
                return zoneEntity;
            }
        }
        throw new DeliveryNotAvailableException();
    }

    public void assignZone(@NonNull String sellerId, @NonNull String zoneId) {
        SEFilter filter = new SEFilter(SEFilterType.AND);
        filter.addClause(WhereClause.eq(ZoneEntity.Fields.zoneId, zoneId));
        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        ZoneEntity zoneEntity = zoneService.repoFindOne(filter);

        Preconditions.check(Objects.nonNull(zoneEntity), ResponseCode.NO_RECORD);

        Optional<Seller> optionalSeller = sellerService.findById(sellerId);

        Preconditions.check(optionalSeller.isPresent(), ResponseCode.SELLER_NOT_FOUND);
        Seller seller = optionalSeller.get();
        seller.setDeliverableZones(zoneId);

        sellerService.update(seller.getId(), seller, Defaults.SYSTEM_ADMIN);

    }

    public Seller getSellerByZone(String zoneId, double lat, double lng) {
//        SEFilter filter = new SEFilter(SEFilterType.AND);
//        filter.addClause(WhereClause.eq(ZoneEntity.Fields.zoneId, zoneId));
//        filter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
//        ZoneEntity zoneEntity = zoneService.repoFindOne(filter);
//        Preconditions.check(Objects.nonNull(zoneEntity), ResponseCode.NO_RECORD);

        SEFilter sellerFilter = new SEFilter(SEFilterType.AND);
        sellerFilter.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));
        sellerFilter.addClause(WhereClause.in(Seller.Fields.deliverableZones, List.of(zoneId)));

        List<Seller> sellers = sellerService.repoFind(sellerFilter);

        if (CollectionUtils.isEmpty(sellers)) {
            throw new DeliveryNotAvailableException();
        }

        if (sellers.size() == 1) {
            return sellers.get(0);
        }

        List<String> addressIds = sellers.stream().map(Seller::getAddress_id).toList();


        SEFilter filterAddress = new SEFilter(SEFilterType.AND);
        filterAddress.addClause(WhereClause.in(BaseMongoEntity.Fields.id, addressIds));
        filterAddress.addClause(WhereClause.eq(BaseMongoEntity.Fields.deleted, false));

        List<Address> addresses = addressService.repoFind(filterAddress);

        if (CollectionUtils.isEmpty(addresses)) {
            throw new DeliveryNotAvailableException();
        }

        Map<String, Address> addressMap = addresses.stream()
                .collect(Collectors.toMap(Address::getId, a -> a));

        Seller nearestSeller = null;
        double minDistance = Double.MAX_VALUE;

        for (Seller seller : sellers) {
            Address address = addressMap.get(seller.getAddress_id());
            if (address == null) continue;

            double sellerLat = address.getLat().doubleValue();
            double sellerLng = address.getLng().doubleValue();

            double distance = DistanceUtils.distanceInKm(
                    lat, lng,
                    sellerLat, sellerLng
            );

            if (distance < minDistance) {
                minDistance = distance;
                nearestSeller = seller;
            }
        }

        return nearestSeller;
    }

}
